import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffCandleSource;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffInstrumentServiceFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffMarketDataServiceFactory;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.SqliteInstrumentRepository;
import com.siberalt.singularity.runtime.progress.ConsoleProgressTrackerFactory;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.utils.entity.CandleMigrationCheckpointRepository;
import com.siberalt.singularity.utils.entity.CandleMigrationService;
import com.siberalt.singularity.utils.entity.SqliteCandleMigrationCheckpointRepository;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

/**
 * Loads minute candles for one or more instruments into the local database.
 * <p>
 * Instruments are named on the command line - by uid, ISIN or ticker, whatever
 * {@link InstrumentService#get} accepts - and the default is the one below, so a run with no
 * arguments does what it always did. Several at a time because the question these candles answer is
 * about a strategy rather than about a share, and two instruments were never enough to answer it.
 * <p>
 * Each instrument has its listing saved before its candles are asked for. Candles are keyed by our
 * instrument, so there has to be one; and a broker knows the name, ISIN, lot and currency that a row
 * created by a migration could only guess at, so saving it here is also what fills in the
 * placeholders the migration had to invent for history already on disk.
 */
public class FetchTinkoffCandles {
    private static final String DEFAULT_INSTRUMENT = "TMOS";
    private static final CandleInterval INTERVAL = CandleInterval.MIN_1;
    // Лимит Tinkoff API для минутных свечей — до 1 дня за один запрос
    // (https://developer.tbank.ru/invest/services/quotes/faq_marketdata)
    private static final int CHUNK_SIZE_DAYS = 1;
    // Сколько чанков одного инструмента запрашивать параллельно (сеть - узкое место,
    // не сам Tinkoff API; консервативное значение, чтобы не упереться в rate limit)
    private static final int CHUNK_PARALLELISM = 5;
    private static final Instant FROM = Instant.parse("2021-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2027-01-01T00:00:00Z");

    public static void main(String[] args) throws IOException, AbstractException, SQLException {
        List<String> queries = args.length == 0 ? List.of(DEFAULT_INSTRUMENT) : List.of(args);

        ConfigInterface tinkoffConfig = new YamlConfig(
            Files.newInputStream(Paths.get("src/test/resources/broker/tinkoff/test-settings.yaml"))
        );
        ConfigInterface appConfig = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );

        Properties properties = new Properties();
        properties.put("token", tinkoffConfig.get("readonlyToken"));
        ServiceStubFactory serviceStubFactory = ServiceStubFactory.create(
            ConnectorConfiguration.loadFromProperties(properties)
        );

        String dbPath = ConfigFacade.of(appConfig).getAsString("dbPath");

        try (Connection connection = DriverManager.getConnection(dbPath))
        {
            SqliteCandleRepository candleRepository = new SqliteCandleRepository(connection);
            InstrumentService instrumentService = new TinkoffInstrumentServiceFactory().create(serviceStubFactory);
            MarketDataService marketDataService = new TinkoffMarketDataServiceFactory().create(serviceStubFactory);
            SqliteInstrumentRepository instruments = new SqliteInstrumentRepository(connection);

            // Свечи мигрируются по нашему id, а T-Bank спрашивается по своему uid - его источник берёт
            // из листинга, который сохраняется ниже до того, как о свечах инструмента спросят.
            TinkoffCandleSource candleSource = new TinkoffCandleSource(marketDataService, INTERVAL, instrumentId ->
                instruments.brokerInstrumentIdOf(AbstractTinkoffBroker.ID, instrumentId).orElseThrow(
                    () -> new IllegalStateException("Instrument " + instrumentId + " has no T-Bank listing")));
            // Свечи и чекпойнт пишут через одно соединение из нескольких потоков; их транзакции
            // сериализует монитор соединения, который держат оба репозитория.
            CandleMigrationCheckpointRepository checkpoint = new SqliteCandleMigrationCheckpointRepository(connection);

            CandleMigrationService migrationService = CandleMigrationService.builder(candleSource, candleRepository)
                .progressTrackerFactory(new ConsoleProgressTrackerFactory())
                .chunkSizeDays(CHUNK_SIZE_DAYS)
                .checkpoint(checkpoint)
                .chunkParallelism(CHUNK_PARALLELISM)
                .build();

            for (String query : queries) {
                Instrument instrument = instrumentService.get(GetRequest.of(query)).getInstrument();

                if (instrument == null) {
                    throw new IllegalStateException("Instrument not found: " + query);
                }

                instruments.save(AbstractTinkoffBroker.ID, instrument);
                long instrumentId = instruments.idOf(instrument.getUid()).orElseThrow();

                System.out.printf("%n%s (%s, %s), instrument %d, lot %d %s%n",
                    instrument.getName(), instrument.getUid(), instrument.getIsin(),
                    instrumentId, instrument.getLot(), instrument.getCurrency());

                migrationService.migrateInstrument(instrumentId, FROM, TO);
            }
        } finally {
            serviceStubFactory.getChannel().shutdown();
        }
    }
}
