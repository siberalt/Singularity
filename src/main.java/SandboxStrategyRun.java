import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.impl.decorator.PositionRiskManagerUpsideCalculator;
import com.siberalt.singularity.broker.impl.tinkoff.sandbox.TinkoffSandboxBroker;
import com.siberalt.singularity.broker.impl.tinkoff.sandbox.TinkoffSandboxService;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.MoneyValueTranslator;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.entity.operation.BrokerOperationRepository;
import com.siberalt.singularity.event.handler.CandleSaveEventHandler;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.strategy.Strategy;
import com.siberalt.singularity.strategy.impl.BasicTradeStrategy;
import com.siberalt.singularity.strategy.market.position.BaseEntryPriceCalculator;
import com.siberalt.singularity.strategy.observer.Observer;
import com.siberalt.singularity.strategy.upside.SlopeUpsideCalculator;
import com.siberalt.singularity.strategy.upside.ThresholdSwitchUpsideCalculator;
import com.siberalt.singularity.strategy.upside.UpsideSignalAmplifier;
import com.siberalt.singularity.strategy.upside.WindowUpsideCalculator;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

public class SandboxStrategyRun {
    public static void main(String[] args) throws IOException, AbstractException {
        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );

        SqliteCandleRepositoryFactory sqliteCandleRepositoryFactory = new SqliteCandleRepositoryFactory();
        SqliteCandleRepository candleRepository = sqliteCandleRepositoryFactory.create(
            ConfigFacade.of(configuration).getAsString("dbPath")
        );

        configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/test/resources/broker/tinkoff/test-settings.yaml"))
        );
        Properties properties = new Properties();
        properties.put("token", configuration.get("sandboxToken"));
        properties.setProperty("sandbox.enabled", "true");
        var broker = new TinkoffSandboxBroker(ConnectorConfiguration.loadFromProperties(properties));

        CandleSaveEventHandler candleSaveEventHandler = new CandleSaveEventHandler(candleRepository);
        broker.getSubscriptionManager().subscribe(new NewCandleSubscriptionSpec(Set.of()), candleSaveEventHandler);

        String accountId = openTestAccount(
            broker,
            "test-account",
            MoneyValue.newBuilder()
                .setCurrency("RUB")
                .setUnits(1_000_000)
                .build()
        );

        Strategy strategy = createStrategy(candleRepository, broker, accountId);
        Observer observer = new Observer();
        strategy.run(observer);
    }

    public static Strategy createStrategy(
        ReadCandleRepository candleRepository,
        EventSubscriptionBroker broker,
        String accountId
    ) {
        PositionRiskManagerUpsideCalculator riskManagerUpsideCalculator = new PositionRiskManagerUpsideCalculator(
            accountId,
            new BaseEntryPriceCalculator(new BrokerOperationRepository(broker.getOperationsService())),
            ATRVolatilityCalculator.ofMultiplier(2)
        );
        SlopeUpsideCalculator slopeUpsideCalculator = new SlopeUpsideCalculator(5);

        ThresholdSwitchUpsideCalculator switcherUpsideCalculator = new ThresholdSwitchUpsideCalculator(
            new UpsideSignalAmplifier(slopeUpsideCalculator, 0.9, 0.9),
            riskManagerUpsideCalculator,
            0.8,
            -0.8
        );

        BasicTradeStrategy strategy = new BasicTradeStrategy(
            broker,
            "TMOS",
            accountId,
            new WindowUpsideCalculator(switcherUpsideCalculator, 60 * 24),
            candleRepository
        );
        strategy.setLookbackCandles(60 * 24);
        strategy.setBuyThreshold(0.9);
        strategy.setSellThreshold(-0.9);
        strategy.setStep(1);

        return strategy;
    }

    protected static String openTestAccount(
        TinkoffSandboxBroker tinkoffBroker,
        String name,
        MoneyValue startBalance
    ) throws AbstractException {
        var responseAccounts = tinkoffBroker.getUserService().getAccounts(null);
        TinkoffSandboxService sandboxService = tinkoffBroker.getSandboxService();

        if (!responseAccounts.getAccounts().isEmpty()) {
            responseAccounts
                .getAccounts()
                .stream()
                .map(Account::getId)
                .forEach(sandboxService::closeAccount);
        }

        var testAccountId = sandboxService.openAccount(name);
        sandboxService.payIn(testAccountId, MoneyValueTranslator.toContract(startBalance));

        return testAccountId;
    }
}
