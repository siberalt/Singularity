import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffCandleSource;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffInstrumentServiceFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffMarketDataServiceFactory;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.entity.instrument.Instrument;
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
import java.util.Properties;

public class FetchTinkoffCandles {
    private static final String INSTRUMENT_QUERY = "b9dff600-4ca6-4fa9-ba91-df2126548ccc";
    private static final CandleInterval INTERVAL = CandleInterval.MIN_1;
    // Лимит Tinkoff API для минутных свечей — до 1 дня за один запрос
    // (https://developer.tbank.ru/invest/services/quotes/faq_marketdata)
    private static final int CHUNK_SIZE_DAYS = 1;
    // Сколько чанков одного инструмента запрашивать параллельно (сеть - узкое место,
    // не сам Tinkoff API; консервативное значение, чтобы не упереться в rate limit)
    private static final int CHUNK_PARALLELISM = 5;
    private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2027-01-01T00:00:00Z");

    public static void main(String[] args) throws IOException, AbstractException, SQLException {
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

        try (
            SqliteCandleRepository candleRepository = new SqliteCandleRepositoryFactory().create(dbPath);
            Connection checkpointConnection = DriverManager.getConnection(dbPath)
        ) {
            InstrumentService instrumentService = new TinkoffInstrumentServiceFactory().create(serviceStubFactory);
            MarketDataService marketDataService = new TinkoffMarketDataServiceFactory().create(serviceStubFactory);

            Instrument instrument = instrumentService.get(GetRequest.of(INSTRUMENT_QUERY)).getInstrument();
            if (instrument == null) {
                throw new IllegalStateException("Instrument not found: " + INSTRUMENT_QUERY);
            }
            String instrumentUid = instrument.getUid();

            TinkoffCandleSource candleSource = new TinkoffCandleSource(marketDataService, INTERVAL);
            CandleMigrationCheckpointRepository checkpoint = new SqliteCandleMigrationCheckpointRepository(checkpointConnection);

            CandleMigrationService migrationService = CandleMigrationService.builder(candleSource, candleRepository)
                .progressTrackerFactory(new ConsoleProgressTrackerFactory())
                .chunkSizeDays(CHUNK_SIZE_DAYS)
                .checkpoint(checkpoint)
                .chunkParallelism(CHUNK_PARALLELISM)
                .build();
            migrationService.migrateInstrument(instrumentUid, FROM, TO);
        } finally {
            serviceStubFactory.getChannel().shutdown();
        }
    }
}
