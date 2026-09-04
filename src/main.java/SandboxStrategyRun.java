import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.contract.service.operation.response.GetPositionsResponse;
import com.siberalt.singularity.broker.contract.service.order.LoggingOrderService;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.decorator.PositionRiskManagerUpsideCalculator;
import com.siberalt.singularity.broker.impl.tinkoff.sandbox.TinkoffSandboxBroker;
import com.siberalt.singularity.broker.impl.tinkoff.sandbox.TinkoffSandboxBrokerFactory;
import com.siberalt.singularity.broker.impl.tinkoff.sandbox.TinkoffSandboxService;
import com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffServicesFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.DecoratingServiceFactory;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffOrderServiceFactory;
import com.siberalt.singularity.broker.shared.BrokerFacade;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.entity.operation.BrokerOperationRepository;
import com.siberalt.singularity.entity.candle.CandleSaveEventHandler;
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
import ru.ttech.piapi.core.connector.ConnectorConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import java.util.Set;

public class SandboxStrategyRun {
    private static final String INSTRUMENT_UID = "55371b1f-8f7c-4c12-9d93-386fae5ec12a";

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
        var broker = TinkoffSandboxBrokerFactory.create(
            ConnectorConfiguration.loadFromProperties(properties),
            new TinkoffServicesFactory().orderServiceFactory(
                new DecoratingServiceFactory<>(new TinkoffOrderServiceFactory(), LoggingOrderService::new)
            )
        );

        CandleSaveEventHandler candleSaveEventHandler = new CandleSaveEventHandler(candleRepository);
        broker.getSubscriptionManager().subscribe(new NewCandleSubscriptionSpec(Set.of()), candleSaveEventHandler);

        Money initialInvestment = Money.of("RUB", Quotation.of(1_000_000));
        String accountId = openTestAccount(broker, "test-account", initialInvestment);

        Strategy strategy = createStrategy(candleRepository, broker, accountId);
        Observer observer = new Observer();

        Instant startTime = Instant.now();
        strategy.run(observer);

        System.out.println("Strategy is running. Press Enter to stop and print results...");
        new BufferedReader(new InputStreamReader(System.in)).readLine();

        strategy.stop();
        System.out.println("Strategy stopped.");

        printResults(broker, accountId, initialInvestment, startTime);
    }

    private static void printResults(
        EventSubscriptionBroker broker,
        String accountId,
        Money initialInvestment,
        Instant startTime
    ) throws AbstractException {
        // Liquidate whatever's left of the position first - comparing cash-to-cash against the
        // initial investment is the objective read on how the strategy did, without an open
        // position's unrealized (and still-moving) value blurring the number.
        BrokerFacade.of(broker).closePosition(accountId, INSTRUMENT_UID);

        GetPositionsResponse positions = broker.getOperationsService().getPositions(GetPositionsRequest.of(accountId));
        Money balance = positions.getMoney().stream()
            .filter(money -> money.getCurrencyIso().equals(initialInvestment.getCurrencyIso()))
            .findFirst()
            .orElse(Money.of(initialInvestment.getCurrencyIso(), Quotation.ZERO));

        Money profit = balance.subtract(initialInvestment);
        double profitPercent = profit.div(initialInvestment).multiply(100).getQuotation().toDouble();

        // Fractional days, not Duration::toDays - an interactive run is typically minutes long, and
        // toDays() truncates to 0 for anything under 24h, which would make APY divide by zero.
        double elapsedDays = Duration.between(startTime, Instant.now()).toMillis() / 86_400_000.0;
        double apy = elapsedDays > 0 ? (profitPercent * 365) / elapsedDays : 0;

        System.out.println("----------------------------");
        System.out.println("Balance: " + balance);
        System.out.println("Absolute profit: " + profit);
        System.out.printf("Absolute profit percent: %.2f%%%n", profitPercent);
        System.out.printf("APY: %.2f%%%n", apy);
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
            INSTRUMENT_UID,
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
        Money startBalance
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
        sandboxService.payIn(testAccountId, startBalance);

        return testAccountId;
    }
}
