import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.mock.EventMockBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.AggregatingCandleRepository;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.entity.instrument.InMemoryInstrumentRepository;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.SqliteInstrumentRepository;
import com.siberalt.singularity.entity.operation.InMemoryOperationRepository;
import com.siberalt.singularity.entity.order.InMemoryOrderRepository;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import com.siberalt.singularity.strategy.impl.BasicTradeStrategy;
import com.siberalt.singularity.strategy.simulation.runner.StrategyBacktester;
import com.siberalt.singularity.strategy.simulation.runner.StrategyResult;
import com.siberalt.singularity.strategy.upside.FilterUpsideCalculator;
import com.siberalt.singularity.strategy.upside.SignalCondition;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.upside.WindowUpsideCalculator;
import com.siberalt.singularity.strategy.upside.condition.Deadband;
import com.siberalt.singularity.strategy.upside.condition.RsiSide;
import com.siberalt.singularity.strategy.upside.trend.MacdUpsideCalculator;
import com.siberalt.singularity.strategy.upside.trend.MovingAverageCrossUpsideCalculator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Золотой крест через событийный симулятор, на дневных свечах.
 * <p>
 * Стенд считает это правило кривой счёта, и с собственной бухгалтерией: деньги между сделками в фонде,
 * дивиденды из календаря, вес по годовому диапазону. Симулятор не знает ничего из этого — он знает заявки,
 * заливы, комиссию и спред. Поэтому числа отсюда и оттуда не сравниваются напрямую: здесь меряется, что
 * делает <b>правило</b>, когда его исполняет брокер, а не что делает счёт.
 * <p>
 * Дневные свечи приходят через {@link AggregatingCandleRepository}: подписка на свечи интервала не знает, и
 * без неё стратегии пришлось бы собирать SMA(200) по дням из ста двадцати тысяч минуток в окне. Заодно часы
 * симуляции идут днями, что для правила с удержанием в месяцы и есть правильная гранулярность; цена этого —
 * внутридневной информации нет, залив идёт по дневной цене.
 * <p>
 * Сигнал — {@link MovingAverageCrossUpsideCalculator}: сторона, а не сила, поэтому пороги покупки и продажи
 * стоят на единице. Окно стратегии — 220 дневных баров: двести на медленную среднюю и запас, чтобы первый
 * же бар после прогрева давал ответ.
 * <p>
 * Чего здесь нет по сравнению со стендом: парковки в фонде (симулятор держит рубли под нулём), дивидендов
 * (их нет в ценах базы) и веса по годовому диапазону. Первые два занижают результат, третий на первом
 * эшелоне его скорее завышал. Так что это проверка механики, а не повтор стендовых чисел.
 */
public class GoldenCrossSimulation {
    private static final long[] INSTRUMENTS = {4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
        21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37};
    private static final Instant FROM = Instant.parse("2021-01-04T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-16T00:00:00Z");
    private static final double COMMISSION = 0.0005;
    private static final Money INITIAL = Money.of("RUB", 1_000_000.00);

    /**
     * Сигнал, пропущенный через фильтры этого прогона: мёртвая зона внутри, RSI снаружи.
     * <p>
     * Порядок не произволен. Мёртвая зона читает силу самого сигнала, поэтому стоит прямо на нём. RSI
     * читает только сторону, поэтому стоит снаружи и спрашивает сигнал лишь тогда, когда зона его
     * пропустила - за этим и нужен {@link SignalCondition}.
     * <p>
     * Оба фильтра выключены, пока их не попросили: {@code deadband=0} и {@code rsi=} пустой.
     */
    static UpsideCalculator filtered(UpsideCalculator trend, double deadband, String deadbandOn,
                                     RsiSide rsi) {
        UpsideCalculator filteredTrend = trend;

        if (deadband > 0) {
            // Сторона решает многое: на обе стороны зона задерживает и закрытие позиции, а это измерено
            // в двадцать семь пунктов на одной сделке.
            SignalCondition zone = switch (deadbandOn) {
                case "entry" -> new Deadband(deadband).onlyForBuys();
                case "exit" -> new Deadband(deadband).onlyForSells();
                default -> new Deadband(deadband);
            };

            filteredTrend = new FilterUpsideCalculator(filteredTrend, zone);
        }

        if (rsi != null) {
            filteredTrend = new FilterUpsideCalculator(filteredTrend, rsi);
        }

        return filteredTrend;
    }

    /** Чем мерить тренд в этом прогоне - единственное, что отличает варианты друг от друга. */
    static UpsideCalculator trendOf(String macd, boolean ema, int fast, int slow, int signal) {
        if (macd.equals("line")) {
            return new MacdUpsideCalculator(fast, slow, signal, MacdUpsideCalculator.Source.LINE);
        }

        if (macd.equals("histogram")) {
            return new MacdUpsideCalculator(fast, slow, signal, MacdUpsideCalculator.Source.HISTOGRAM);
        }

        return ema ? MovingAverageCrossUpsideCalculator.ofEma(fast, slow)
            : MovingAverageCrossUpsideCalculator.ofSma(fast, slow);
    }

    public static void main(String[] args) throws Exception {
        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml")));
        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepository minutes = new SqliteCandleRepositoryFactory().create(dbPath);
        SqliteInstrumentRepository instruments =
            new SqliteInstrumentRepository(DriverManager.getConnection(dbPath));
        Map<String, String> options = new HashMap<>();

        for (String argument : args) {
            int at = argument.indexOf('=');

            options.put(argument.substring(0, at), argument.substring(at + 1));
        }

        long[] chosen = options.containsKey("ids")
            ? Arrays.stream(options.get("ids").split(",")).mapToLong(Long::parseLong).toArray()
            : INSTRUMENTS;
        int fast = Integer.parseInt(options.getOrDefault("fast", "50"));
        int slow = Integer.parseInt(options.getOrDefault("slow", "200"));
        boolean ema = options.getOrDefault("ema", "0").equals("1");
        // macd=line - сторона самой линии (пересечение двух EMA), macd=histogram - сторона зазора между
        // линией и её средней. Пусто - пересечение простых средних, как было.
        String macd = options.getOrDefault("macd", "");
        int signalPeriod = Integer.parseInt(options.getOrDefault("signal", "20"));
        // Окно, которое копит WindowUpsideCalculator. Для SMA хватает периода медленной средней, а EMA
        // зависит от всей истории - усечённое окно даёт другой индикатор, и это надо уметь проверить.
        int window = Integer.parseInt(options.getOrDefault("window", String.valueOf(slow + 20)));
        // Мёртвая зона в долях цены: 0.002 - линия должна отойти от нуля на 0.2% цены, иначе её сторона
        // считается шумом. Ноль - зоны нет.
        double deadband = Double.parseDouble(options.getOrDefault("deadband", "0"));
        // На какую сторону действует зона: both - на обе, entry - только на вход, exit - только на выход.
        String deadbandOn = options.getOrDefault("deadbandOn", "both");
        int rsiPeriod = Integer.parseInt(options.getOrDefault("rsiPeriod", "14"));
        // rsi=50 - покупать только в нижней половине, продавать только в верхней. rsi=50,0 - то же самое
        // для входа, но любой выход пропускается: в этом правиле сигнал и открывает, и закрывает позицию,
        // поэтому симметричный фильтр вето́ит выход по мёртвому кресту ровно на падении, когда RSI низкий.
        String[] levels = options.getOrDefault("rsi", "").split(",");
        RsiSide rsi = levels[0].isEmpty() ? null : new RsiSide(rsiPeriod,
            Double.parseDouble(levels[0]),
            Double.parseDouble(levels.length > 1 ? levels[1] : levels[0]));
        ReadCandleRepository candles = new AggregatingCandleRepository(minutes, CandleInterval.DAY);

        System.out.printf(Locale.ROOT,
            "%s .. %s: %s(%d, %d) на дневных свечах, комиссия %.3f%% за сторону%n", FROM, TO,
            macd.isEmpty() ? (ema ? "пересечение EMA" : "пересечение SMA") : "MACD по " + macd,
            fast, slow, 100 * COMMISSION);
        System.out.printf(Locale.ROOT, "фильтры: мёртвая зона %s, RSI %s%n",
            deadband > 0 ? String.format(Locale.ROOT, "%.3f%% цены на %s", 100 * deadband, deadbandOn)
                : "нет",
            rsi == null ? "нет" : "(" + options.get("rsi") + "), период " + rsiPeriod);
        System.out.printf("%4s %12s %8s %8s %11s %10s%n",
            "id", "счёт", "сделок", "плюс", "средняя %", "в рынке");

        List<Double> profits = new ArrayList<>();

        for (long id : chosen) {
            String uid = instruments.brokerInstrumentIdOf(AbstractTinkoffBroker.ID, id).orElseThrow();
            InMemoryInstrumentRepository listing = new InMemoryInstrumentRepository();

            listing.save(EventMockBroker.DEFAULT_ID, new Instrument()
                .setInstrumentType(InstrumentType.SHARE)
                .setLot(1)
                .setCurrency("RUB")
                .setUid(uid));

            InMemoryOperationRepository operations = new InMemoryOperationRepository();
            SimpleSimulationClock clock = new SimpleSimulationClock();
            EventMockBroker broker = EventMockBroker.builder(candles, instruments, listing,
                    new InMemoryOrderRepository(), operations, clock)
                .setCommissionRatio(COMMISSION)
                // Задержка по умолчанию - один бар: сигнал считается по закрытию дня, а заявка доходит до
                // рынка на следующий. Нулевая позволила бы торговать по той же свече, которую стратегия
                // уже дочитала до конца.
                .build();

            broker.getOrderService().getPriceModel()
                .setHalfSpreadRatio(0.00015)
                .setSlippageImpactRatio(0.0006);
            broker.getOrderService().getLiquidityModel().setInfiniteLiquidity(true);

            StrategyResult result = new StrategyBacktester<EventMockBroker>(
                (range, accountId, simulated, observer) -> new BasicTradeStrategy(simulated, uid, accountId,
                    // Окно копит сам калькулятор окна: стратегия отдаёт свечи по одной и очищает список.
                    new WindowUpsideCalculator(
                        filtered(trendOf(macd, ema, fast, slow, signalPeriod), deadband, deadbandOn, rsi),
                        window),
                    candles)
                    .setLookbackCandles(window)
                    .setBuyThreshold(1)
                    .setSellThreshold(-1)
                    .setStep(1)
                    .run(observer),
                broker,
                uid,
                INITIAL,
                clock
            ).run(FROM, TO);

            List<RsiLimitEntrySimulation.Trade> trades = RsiLimitEntrySimulation.tradesOf(
                operations.getByAccountId(result.accountId(), new TimeRange(FROM, TO)));

            if (!options.getOrDefault("dump", "0").equals("0")) {
                System.out.printf("  %-12s %-12s %9s%n", "вход", "выход", "итог %");

                for (RsiLimitEntrySimulation.Trade trade : trades) {
                    System.out.printf(Locale.ROOT, "  %-12s %-12s %+8.2f%n",
                        trade.entry().toString().substring(0, 10),
                        trade.exit().toString().substring(0, 10), trade.percent());
                }
            }
            long held = trades.stream()
                .mapToLong(trade -> Duration.between(trade.entry(), trade.exit()).toDays())
                .sum();

            profits.add(result.profitPercent());

            System.out.printf(Locale.ROOT, "%4d %+12.2f %8d %7.0f%% %+11.2f %9.0f%%%n", id,
                result.profitPercent(), trades.size(),
                100.0 * trades.stream().filter(trade -> trade.percent() > 0).count()
                    / Math.max(1, trades.size()),
                trades.stream().mapToDouble(RsiLimitEntrySimulation.Trade::percent).average().orElse(0),
                100.0 * held / Duration.between(FROM, TO).toDays());
        }

        System.out.printf(Locale.ROOT, "%nсреднее по бумагам %+.2f%%, положительных %d из %d%n",
            profits.stream().mapToDouble(Double::doubleValue).average().orElse(0),
            profits.stream().filter(profit -> profit > 0).count(), profits.size());
    }
}
