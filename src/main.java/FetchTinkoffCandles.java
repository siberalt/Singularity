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
import com.siberalt.singularity.utils.entity.CandleMigrationResult;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
 * <p>
 * One instrument failing does not stop the others: the network to T-Bank drops for minutes at a
 * time, and a run of many instruments that dies on the first drop leaves every later one unloaded.
 * What did not load - an instrument that could not be looked up, or chunks that could not be
 * fetched - is listed at the end, and the process exits with a non-zero code, so a script re-running
 * it can tell a finished load from one with holes. The checkpoint makes the re-run fetch only those.
 */
public class FetchTinkoffCandles {
    private static final String DEFAULT_INSTRUMENT = "55371b1f-8f7c-4c12-9d93-386fae5ec12a";
    private static final CandleInterval INTERVAL = CandleInterval.MIN_1;
    // Лимит Tinkoff API для минутных свечей — до 1 дня за один запрос
    // (https://developer.tbank.ru/invest/services/quotes/faq_marketdata)
    private static final int CHUNK_SIZE_DAYS = 1;
    // Сколько чанков одного инструмента запрашивать параллельно (сеть - узкое место,
    // не сам Tinkoff API; консервативное значение, чтобы не упереться в rate limit)
    private static final int CHUNK_PARALLELISM = 5;
    // Лимит запросов выдаётся на окно времени: за проход успевает пройти около шестисот чанков, дальше
    // отказы идут пачкой. Повторы внутри миграции ждут нового окна сами - шести хватает на пять лет
    // минуток, которые без них приходилось добирать перезапусками процесса.
    private static final int RETRIES = 6;
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(15);
    private static final Instant FROM = Instant.parse("2021-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-21T00:00:00Z");

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

        List<String> incomplete = new ArrayList<>();

        try (Connection connection = DriverManager.getConnection(dbPath)) {
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
                .retries(RETRIES)
                .retryBackoff(RETRY_BACKOFF)
                .build();

            for (String query : queries) {
                try {
                    Instrument instrument = instrumentService.get(GetRequest.of(query)).getInstrument();

                    if (instrument == null) {
                        incomplete.add(query + ": not found at T-Bank");
                        continue;
                    }

                    instruments.save(AbstractTinkoffBroker.ID, instrument);
                    long instrumentId = instruments.idOf(instrument.getUid()).orElseThrow();

                    System.out.printf("%n%s (%s, %s), instrument %d, lot %d %s%n",
                        instrument.getName(), instrument.getUid(), instrument.getIsin(),
                        instrumentId, instrument.getLot(), instrument.getCurrency());

                    CandleMigrationResult result = migrationService.migrateInstrument(instrumentId, FROM, TO);

                    System.out.printf("%n%s: %d candles saved, %d of %d chunks already done, %d failed%n",
                        instrument.getName(), result.savedCandles(), result.skippedChunks(),
                        result.totalChunks(), result.failedChunks().size());

                    if (!result.isComplete()) {
                        incomplete.add(String.format("%s (%s): %d chunks failed, first from %s",
                            instrument.getName(), query, result.failedChunks().size(),
                            result.failedChunks().getFirst().from()));
                    }
                } catch (AbstractException | RuntimeException e) {
                    // Usually the network to T-Bank dropping; the next instrument may well get through.
                    System.err.printf("%n%s: %s%n", query, e.getMessage());
                    incomplete.add(query + ": " + e.getClass().getSimpleName());
                }
            }
        } finally {
            serviceStubFactory.getChannel().shutdown();
        }

        if (!incomplete.isEmpty()) {
            System.err.printf("%nNot fully loaded - run again to fetch what is missing:%n");
            incomplete.forEach(line -> System.err.println("  " + line));
            System.exit(1);
        }
    }
}
