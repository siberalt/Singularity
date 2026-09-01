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
import com.siberalt.singularity.utils.entity.CandleMigrationService;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Properties;

public class FetchTinkoffCandles {
    private static final String INSTRUMENT_QUERY = "55371b1f-8f7c-4c12-9d93-386fae5ec12a";
    private static final CandleInterval INTERVAL = CandleInterval.MIN_1;
    // Лимит Tinkoff API для минутных свечей — до 1 дня за один запрос
    // (https://developer.tbank.ru/invest/services/quotes/faq_marketdata)
    private static final int CHUNK_SIZE_DAYS = 1;
    private static final Instant FROM = Instant.parse("2025-01-01T00:00:00Z");
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

        try (
            SqliteCandleRepository candleRepository = new SqliteCandleRepositoryFactory().create(
                ConfigFacade.of(appConfig).getAsString("dbPath")
            )
        ) {
            InstrumentService instrumentService = new TinkoffInstrumentServiceFactory().create(serviceStubFactory);
            MarketDataService marketDataService = new TinkoffMarketDataServiceFactory().create(serviceStubFactory);

            Instrument instrument = instrumentService.get(GetRequest.of(INSTRUMENT_QUERY)).getInstrument();
            if (instrument == null) {
                throw new IllegalStateException("Instrument not found: " + INSTRUMENT_QUERY);
            }
            String instrumentUid = instrument.getUid();

            TinkoffCandleSource candleSource = new TinkoffCandleSource(marketDataService, INTERVAL);

            CandleMigrationService migrationService = new CandleMigrationService(
                new ConsoleProgressTrackerFactory(),
                candleSource,
                candleRepository,
                1,
                CHUNK_SIZE_DAYS
            );
            migrationService.migrateInstrument(instrumentUid, FROM, TO);
        } finally {
            serviceStubFactory.getChannel().shutdown();
        }
    }
}
