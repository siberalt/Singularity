import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleAggregator;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.strategy.indicator.Sma;
import com.siberalt.singularity.math.LinearRegression;
import com.siberalt.singularity.strategy.extreme.ExtremePositions;
import com.siberalt.singularity.strategy.extreme.ZigZagExtremeLocator;
import com.siberalt.singularity.strategy.market.CrossingRegime;
import com.siberalt.singularity.strategy.market.DecisiveCrossing;
import com.siberalt.singularity.strategy.market.DistanceBand;
import com.siberalt.singularity.strategy.market.MarketCoefficient;
import com.siberalt.singularity.strategy.market.MarketSeries;
import com.siberalt.singularity.strategy.market.SteadySide;
import com.siberalt.singularity.strategy.market.TrendAngle;
import com.siberalt.singularity.strategy.market.TrendZone;
import com.siberalt.singularity.strategy.market.TrendZones;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Зоны режима по всей истории: восходящий, нисходящий, флэт - и ряд, по чьим изгибам они размечены.
 * Файл читает {@code TrendZones.html}.
 *
 * <h2>Чем это меряет режим</h2>
 * {@link TrendAngle} по SMA({@link #SMA_PERIOD}) под {@link SteadySide} с двумя порогами. Угол - это
 * {@code Σdᵢ / Σ|dᵢ|} по ряду средней, то есть односторонность её хода: предел ±45° означает, что средняя
 * не изгибалась ни разу, ноль - что изгибалась туда и обратно в ничью. Значимость изгиба мерится
 * пройденным средней путём, а не ошибкой разности средних: цены автокоррелированы, и классический
 * критерий объявлял бы тренд почти всегда.
 * <p>
 * Или {@link DecisiveCrossing} на той же средней: сторона меняется на пересечении цены со средней,
 * но только если наклон цены в сторону пересечения решителен - иначе средняя просто всплыла над
 * стоящей ценой, и это не смена тренда. Флэта у него нет, сторон две. Рядом с зонами печатается
 * контроль - та же средняя без подтверждения, - чтобы было видно, сколько смен стороны отсечено.
 *
 * <h2>Почему здесь нет кадров, в отличие от {@code LevelDetectorSimulation}</h2>
 * Там кадры нужны по существу: ответ детектора зависит от окна, которое ему дали, и одно и то же место
 * истории из разных окон выглядит по-разному. Режим на баре {@code t} - функция баров до {@code t}, и
 * ответ на каждом баре один. Шагать не по чему; вся разметка - одна полоса.
 * <p>
 * Зато из этого следует то, на что и надо смотреть глазами: разметка <b>причинная</b>, поэтому начало
 * зоны всегда позже настоящего изгиба - ровно на столько, сколько стоит объявление стороны. Карта задним
 * числом была бы ровнее и бесполезнее.
 *
 * <h2>Что уже измерено и чего от картинки ждать</h2>
 * На ликвидной трети дневными барами эта разметка заняла: вверх 40-56% баров, вниз 33-49%, <b>флэт
 * 6-11%</b>. То есть на этих порогах трихотомия фактически дихотомия, и именно это на картинке стоит
 * проверить глазами - где флэт всё-таки нашёлся и похож ли он на флэт. Причина измеренная: сглаженный ряд
 * разворачивается редко, его {@code Σ|dᵢ|} мал, и угол почти всё время сидит у предела, так что 0.6 для
 * угла средней - мягкий порог, а не строгий.
 * <p>
 * Торговый итог той же разметки, чтобы картинка не обманывала: как парковочное правило она обошла
 * удержание бумаги (+47.9 ± 13.4 пп), фонд не обошла (+3.2 ± 9.2), а гистерезис против его отсутствия дал
 * +1.9 ± 2.9 - то есть не дал ничего.
 * <p>
 * У {@link DistanceBand} то же самое: против удержания проходит (+47.7 ± 11.9 статическая, +53.3 ± 16.1 с
 * сужением), фонд не обходит, а сторону цены без полосы не обгоняет ни статическая полоса (−8.1 ± 14.3),
 * ни с сужением (−2.5 ± 6.6) - полоса покупает меньше сделок, 2-3 в год против 12, а не доходность. Всё
 * это мерилось как самостоятельное правило «бумага или фонд»; задумана разметка как фильтр для другой
 * стратегии, и мерить её как фильтр надо отдельно.
 */
public class TrendZoneSimulation {
    /** Самая ликвидная бумага базы по медианному минутному обороту - с неё начинаются все стенды. */
    private static final long INSTRUMENT_ID = 7;
    private static final Instant FROM = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-12-16T00:00:00Z");
    /** Дневные бары: режим - вещь медленная, и мерился он дневными. */
    private static final CandleInterval INTERVAL = CandleInterval.DAY;
    /** Период средней, по чьим изгибам читается тренд: быстрая нога креста, которым проект уже мерил. */
    private static final int SMA_PERIOD = 80;
    /** Сколько приращений средней входит в угол. Месяц торговых дней, и он не больше периода. */
    private static final int ANGLE_BARS = 40;
    /** Какое чтение объявляет сторону, долей предела. 0.6 - это 27°. */
    private static final double DECISIVE = 0.9;
    /** При каком согласном чтении объявленная сторона остаётся. Производный от решительного: половина. */
    private static final double REMAINS = DECISIVE / 2;
    /** Окрестность, в которой считаются пересечения цены со средней. */
    private static final int CROSS_WINDOW = 20;
    /** Сколько пересечений в окрестности делают её флэтом. */
    private static final int CROWD = 3;
    /**
     * По скольким приращениям цены проводится наклон, подтверждающий пересечение, - неделя торговых
     * дней. С якорем {@link #SLOPE_ANCHOR} это потолок ноги от разворота.
     */
    private static final int SLOPE_BARS = 5;
    /** Какой наклон подтверждает пересечение, в ATR за бар: за неделю это ход на 3 ATR. */
    private static final double SLOPE_DECISIVE = 0.6;
    /**
     * Порог отката зигзага, от чьего последнего разворота проводится наклон. Ноль - без якоря, наклон по
     * последним {@link #SLOPE_BARS} барам.
     */
    private static final double SLOPE_ANCHOR = 1;
    /**
     * Сколько баров после прогрева средней помнит {@link DecisiveCrossing}. Сторону он не хранит, а
     * пересчитывает по окну и засевает по первому читаемому бару - так что разногласие старше окна
     * становится стороной без всякого подтверждения. Окно {@code SMA_PERIOD + ANGLE_BARS} оставило бы ему
     * сорок баров памяти, и гистерезис терял бы смысл через два месяца; год - с запасом.
     */
    private static final int MEMORY = 400;

    /**
     * Чем размечать режим. Переключатель, а не три стенда: все разметки должны ложиться на одну и ту же
     * среднюю, иначе сравнивать картинки нечем.
     */
    enum Regime {
        /** Угол средней под {@link SteadySide}. */
        ANGLE,
        /** Частота пересечений цены со средней. */
        CROSSINGS,
        /** Пересечение со средней, подтверждённое наклоном цены. */
        DECISIVE,
        /** Расстояние цены от средней в ATR, с полосой гистерезиса. */
        BAND
    }

    private static final Regime REGIME = Regime.BAND;
    /**
     * Полуширина полосы {@link DistanceBand} в ATR(14). Объявлена до того, как смотреть картинку: медиана
     * {@code |цена − SMA(80)| / ATR(14)} по ликвидной трети дневными барами, 2.48, округлённая.
     */
    private static final double BAND_WIDTH = 2.5;
    /**
     * Сужение краёв полосы по наклону цены, объявленное до стенда: наклон по неделе торговых дней,
     * проекция на тот же горизонт, края не уже одного ATR - хода типичного дня. Ноль в
     * {@link #BAND_HORIZON} - статическая полоса.
     */
    private static final int BAND_SLOPE_BARS = 5;
    private static final double BAND_HORIZON = BAND_SLOPE_BARS;
    private static final double BAND_MIN_WIDTH = 1;
    /**
     * Схлопывать ли край полосы до средней, когда цена отбила её решительным наклоном -
     * {@link DistanceBand#setReclaim}. Порог не подбирается: тот наклон, при котором полоса сужается до
     * {@link #BAND_MIN_WIDTH}, - дальше край падает до самой средней. Наклон тот же, что у сужения.
     */
    private static final boolean RECLAIM = true;
    private static final double RECLAIM_DECISIVE = (BAND_WIDTH - BAND_MIN_WIDTH) / BAND_HORIZON;
    private static final Path OUTPUT = Paths.get("src/main/resources/presenter/google/TrendZones.json");

    public static void main(String[] args) throws IOException {
        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );

        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepository candleRepository = new SqliteCandleRepositoryFactory().create(dbPath);
        List<Candle> bars = new CandleAggregator()
            .aggregate(candleRepository.getPeriod(INSTRUMENT_ID, FROM, TO), INTERVAL);
        int window = windowOf(REGIME);

        if (bars.size() <= window) {
            throw new IllegalStateException(
                "В периоде %d баров %s, а мере нужно больше %d".formatted(bars.size(), INTERVAL, window));
        }

        // Окно ровно такое, сколько мере нужно: лишние бары ответа не меняют, см. контракт TrendZones.
        List<TrendZone> zones = TrendZones.of(bars, regime(), window);
        double[] angles = anglesOf(bars, SMA_PERIOD + ANGLE_BARS);
        double[] crossings = crossingsOf(bars);
        double[] slopes = slopesOf(bars);
        double[] distances = band().distancesOf(bars);
        DistanceBand.Edges edges = band().edgesOf(bars);

        write(bars, zones, angles, crossings, slopes, distances, edges);
        report(bars, zones, window);
    }

    private static MarketCoefficient regime() {
        return switch (REGIME) {
            case ANGLE -> new SteadySide(angle(), DECISIVE, REMAINS);
            case CROSSINGS -> new CrossingRegime(curve(), CROSS_WINDOW, CROWD);
            case DECISIVE -> decisiveCrossing(SLOPE_ANCHOR > 0);
            case BAND -> band();
        };
    }

    private static DistanceBand band() {
        return RECLAIM ? narrowed().setReclaim(RECLAIM_DECISIVE) : narrowed();
    }

    private static DistanceBand narrowed() {
        return new DistanceBand(curve(), BAND_WIDTH)
            .setSlopeNarrowing(BAND_SLOPE_BARS, BAND_HORIZON, BAND_MIN_WIDTH);
    }

    private static DecisiveCrossing decisiveCrossing(boolean anchored) {
        DecisiveCrossing crossing = new DecisiveCrossing(curve(), SLOPE_BARS, SLOPE_DECISIVE);

        return anchored ? crossing.setSlopeAnchor(new ZigZagExtremeLocator(SLOPE_ANCHOR)) : crossing;
    }

    private static int windowOf(Regime regime) {
        // Обоим помнить сторону нечем, кроме окна: она пересчитывается по нему на каждом баре.
        return regime == Regime.DECISIVE || regime == Regime.BAND
            ? SMA_PERIOD + MEMORY : SMA_PERIOD + ANGLE_BARS;
    }

    private static TrendAngle angle() {
        return TrendAngle.overSma(SMA_PERIOD, ANGLE_BARS);
    }

    private static MarketSeries curve() {
        return candles -> Sma.seriesOf(candles, SMA_PERIOD, Candle::getCloseAsDouble);
    }

    /**
     * Сколько пересечений цены со средней попало в окрестность каждого бара - то, из чего получились
     * зоны при {@link Regime#CROSSINGS}. На картинке нужно рядом с ними по той же причине, что и угол: по
     * зонам не видно, набралась гроздь еле-еле или с запасом.
     */
    private static double[] crossingsOf(List<Candle> bars) {
        double[] sma = Sma.seriesOf(bars, SMA_PERIOD, Candle::getCloseAsDouble);
        double[] counts = new double[bars.size()];

        for (int at = 0; at < bars.size(); at++) {
            if (Double.isNaN(sma[at])) {
                counts[at] = Double.NaN;

                continue;
            }

            int crossings = 0;
            double previous = 0;

            for (int back = Math.max(0, at - CROSS_WINDOW + 1); back <= at; back++) {
                double side = Math.signum(bars.get(back).getCloseAsDouble() - sma[back]);

                if (side == 0 || Double.isNaN(sma[back])) {
                    continue;
                }

                crossings += previous != 0 && side != previous ? 1 : 0;
                previous = side;
            }

            counts[at] = crossings;
        }

        return counts;
    }

    /**
     * Наклон цены в ATR за бар - то, чем {@link DecisiveCrossing} подтверждает пересечения. Считается
     * тем же способом, что и внутри него: регрессия закрытий от последнего разворота зигзага, но не
     * дальше {@link #SLOPE_BARS} приращений назад, ATR(14) у того же бара. Совпадает с его чтением не до
     * последнего знака - он ведёт ATR и зигзаг от начала своего окна, а здесь от начала истории, - но
     * сглаживание Уайлдера забывает начало за несколько десятков баров, а последний разворот у бара от
     * начала отсчёта почти никогда не зависит.
     */
    private static double[] slopesOf(List<Candle> bars) {
        double[] atr = new ATRVolatilityCalculator().profile(bars);
        double[] readings = new double[bars.size()];
        ZigZagExtremeLocator anchor = SLOPE_ANCHOR > 0 ? new ZigZagExtremeLocator(SLOPE_ANCHOR) : null;

        for (int at = 0; at < bars.size(); at++) {
            int from = at - SLOPE_BARS;

            if (anchor != null) {
                // Только развороты, известные на этом баре: префикс, а не вся история.
                List<Candle> known = bars.subList(0, at + 1);
                int[] pivots = ExtremePositions.of(known, anchor.locate(known));

                from = pivots.length == 0 ? from : Math.max(pivots[pivots.length - 1], from);
            }

            int increments = at - from;

            if (from < 0 || increments < 2 || !(atr[at] > 0)) {
                readings[at] = Double.NaN;

                continue;
            }

            double[] x = new double[increments + 1];
            double[] y = new double[increments + 1];

            for (int step = 0; step <= increments; step++) {
                x[step] = step;
                y[step] = bars.get(from + step).getCloseAsDouble();
            }

            readings[at] = new LinearRegression(x, y).getSlope() / atr[at];
        }

        return readings;
    }

    /**
     * Сам угол на каждом баре - то, из чего получились зоны. На картинке он нужен рядом с ними: по
     * зонам не видно, прошло чтение порог с запасом или еле-еле.
     */
    private static double[] anglesOf(List<Candle> bars, int window) {
        TrendAngle angle = angle();
        double[] readings = new double[bars.size()];

        for (int at = 0; at < bars.size(); at++) {
            readings[at] = angle.of(bars.subList(Math.max(0, at - window + 1), at + 1));
        }

        return readings;
    }

    /** Сколько зон вышло, какой они длины и сколько времени занимает каждая сторона. */
    private static void report(List<Candle> bars, List<TrendZone> zones, int window) {
        System.out.printf(Locale.ROOT, "бумага %d, %d баров %s, зон %d%n",
            INSTRUMENT_ID, bars.size(), INTERVAL, zones.size());
        System.out.printf(Locale.ROOT, "режим: %s%n", switch (REGIME) {
            case ANGLE -> "SteadySide(TrendAngle.overSma(%d, %d), %.2f, %.2f)"
                .formatted(SMA_PERIOD, ANGLE_BARS, DECISIVE, REMAINS);
            case CROSSINGS -> "CrossingRegime(SMA(%d), окрестность %d, гроздь от %d)"
                .formatted(SMA_PERIOD, CROSS_WINDOW, CROWD);
            case DECISIVE -> String.format(Locale.ROOT,
                "DecisiveCrossing(SMA(%d), наклон %s, от %.2f ATR за бар), память %d", SMA_PERIOD,
                SLOPE_ANCHOR > 0
                    ? String.format(Locale.ROOT, "от разворота зигзага %.1f ATR, не длиннее %d",
                        SLOPE_ANCHOR, SLOPE_BARS)
                    : "по " + SLOPE_BARS,
                SLOPE_DECISIVE, MEMORY);
            case BAND -> String.format(Locale.ROOT, "DistanceBand(SMA(%d), полоса ±%.2f ATR, сужение по "
                    + "наклону за %d баров на горизонт %.0f, не уже %.2f%s), память %d",
                SMA_PERIOD, BAND_WIDTH, BAND_SLOPE_BARS, BAND_HORIZON, BAND_MIN_WIDTH,
                RECLAIM ? String.format(Locale.ROOT, ", до средней при наклоне от %.2f", RECLAIM_DECISIVE) : "",
                MEMORY);
        });

        // Полоса сравнивается с собой без отмены, со статической той же ширины, со статической шириной
        // нижнего края, с нулевой шириной - стороной цены - и с пересечением, подтверждённым наклоном, на
        // той же средней и в том же окне. «Против цены» - доля размеченных баров, где сторона режима
        // противоположна стороне цены относительно средней: то, что на картинке видно как ложный сигнал.
        if (REGIME == Regime.BAND) {
            System.out.printf("%-36s %6s %12s%n", "разметка", "зон", "против цены");
            compare(bars, window, "эта", zones);
            compare(bars, window, "без отмены", TrendZones.of(bars, narrowed(), window));
            compare(bars, window, String.format(Locale.ROOT, "статическая ±%.2f", BAND_WIDTH),
                TrendZones.of(bars, new DistanceBand(curve(), BAND_WIDTH), window));
            compare(bars, window, String.format(Locale.ROOT, "статическая ±%.2f с отменой", BAND_WIDTH),
                TrendZones.of(bars, new DistanceBand(curve(), BAND_WIDTH)
                    .setSlopeNarrowing(BAND_SLOPE_BARS, 0, BAND_WIDTH).setReclaim(RECLAIM_DECISIVE), window));
            compare(bars, window, String.format(Locale.ROOT, "статическая ±%.2f", BAND_MIN_WIDTH),
                TrendZones.of(bars, new DistanceBand(curve(), BAND_MIN_WIDTH), window));
            compare(bars, window, "без полосы", TrendZones.of(bars, new DistanceBand(curve(), 0), window));
            compare(bars, window, "пересечение с наклоном",
                TrendZones.of(bars, decisiveCrossing(SLOPE_ANCHOR > 0), window));
        }

        // Контроли - та же средняя без подтверждения и, при якоре, то же подтверждение по последним
        // барам: сколько смен стороны отсекло подтверждение и что из этого добавил якорь.
        if (REGIME == Regime.DECISIVE) {
            int naive = TrendZones.of(bars, DecisiveCrossing.naive(curve()), window).size();

            System.out.printf(Locale.ROOT, "без подтверждения зон %d, отсечено %.0f%% смен стороны%n",
                naive, naive <= 1 ? 0 : 100.0 * (naive - zones.size()) / (naive - 1));

            if (SLOPE_ANCHOR > 0) {
                System.out.printf(Locale.ROOT, "без якоря, по последним %d барам, зон %d%n", SLOPE_BARS,
                    TrendZones.of(bars, decisiveCrossing(false), window).size());
            }
        }
        System.out.printf("%10s %6s %8s %8s %8s%n", "сторона", "зон", "баров", "доля", "медиана");

        for (double side : new double[]{SteadySide.UP, SteadySide.FLAT, SteadySide.DOWN}) {
            int[] lengths = zones.stream()
                .filter(zone -> zone.side() == side)
                .mapToInt(TrendZone::bars)
                .sorted()
                .toArray();
            int total = 0;

            for (int length : lengths) {
                total += length;
            }

            System.out.printf(Locale.ROOT, "%10s %6d %8d %7.0f%% %8s%n", nameOf(side), lengths.length,
                total, 100.0 * total / bars.size(),
                lengths.length == 0 ? "-" : String.valueOf(lengths[lengths.length / 2]));
        }

        System.out.println("записано в " + OUTPUT);
    }

    private static void compare(List<Candle> bars, int window, String name, List<TrendZone> zones) {
        double[] sma = Sma.seriesOf(bars, SMA_PERIOD, Candle::getCloseAsDouble);
        int marked = 0;
        int against = 0;

        for (TrendZone zone : zones) {
            for (int at = zone.from(); at <= zone.to(); at++) {
                marked++;
                against += zone.side() * (bars.get(at).getCloseAsDouble() - sma[at]) < 0 ? 1 : 0;
            }
        }

        System.out.printf(Locale.ROOT, "%-36s %6d %11.0f%%%n", name, zones.size(),
            marked == 0 ? 0 : 100.0 * against / marked);
    }

    private static String nameOf(double side) {
        return side > 0 ? "вверх" : side < 0 ? "вниз" : "флэт";
    }

    /**
     * Ряд, средняя, угол и зоны - одним файлом.
     * <p>
     * {@link Double#NaN} уходит как {@code null}: JSON такого числа не знает, а страница рисует линию, и
     * дырку в ней надо назвать дыркой, а не нулём - ноль у угла означает «ход сошёлся в ничью», то есть
     * прямо противоположное отсутствию чтения.
     */
    private static void write(List<Candle> bars, List<TrendZone> zones, double[] angles,
                              double[] crossings, double[] slopes, double[] distances,
                              DistanceBand.Edges edges) throws IOException {
        double[] sma = Sma.seriesOf(bars, SMA_PERIOD, Candle::getCloseAsDouble);

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(OUTPUT))) {
            out.printf(Locale.ROOT, "{%n  \"instrument\": %d,%n  \"interval\": \"%s\",%n",
                INSTRUMENT_ID, INTERVAL);
            out.printf(Locale.ROOT, "  \"settings\": {\"sma\": %d, \"bars\": %d, \"decisive\": %s,"
                    + " \"remains\": %s, \"window\": %d, \"crowd\": %d, \"slopeBars\": %d,"
                    + " \"slopeDecisive\": %s, \"slopeAnchor\": %s, \"bandWidth\": %s,"
                    + " \"bandSlopeBars\": %d, \"bandHorizon\": %s, \"bandMinWidth\": %s,"
                    + " \"reclaim\": %s, \"regime\": \"%s\"},%n",
                SMA_PERIOD, ANGLE_BARS, DECISIVE, REMAINS, CROSS_WINDOW, CROWD, SLOPE_BARS,
                SLOPE_DECISIVE, SLOPE_ANCHOR, BAND_WIDTH, BAND_SLOPE_BARS, BAND_HORIZON, BAND_MIN_WIDTH,
                RECLAIM ? String.format(Locale.ROOT, "%.4f", RECLAIM_DECISIVE) : "null", REGIME);

            out.print("  \"times\": [");

            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.printf("\"%s\"", bars.get(row).getTime());
            }

            out.printf("],%n  \"price\": [");

            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.printf(Locale.ROOT, "%.4f", bars.get(row).getCloseAsDouble());
            }

            out.printf("],%n  \"sma\": [");

            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.print(numberOf(sma[row], 4));
            }

            out.printf("],%n  \"angle\": [");

            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.print(numberOf(angles[row], 4));
            }

            out.printf("],%n  \"crossings\": [");

            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.print(numberOf(crossings[row], 0));
            }

            out.printf("],%n  \"slope\": [");

            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.print(numberOf(slopes[row], 4));
            }

            out.printf("],%n  \"distance\": [");

            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.print(numberOf(distances[row], 4));
            }

            // Края - там, где расстояние измеримо: до прогрева средней рисовать их не к чему.
            out.printf("],%n  \"bandUp\": [");

            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.print(numberOf(Double.isNaN(distances[row]) ? Double.NaN : edges.up()[row], 4));
            }

            out.printf("],%n  \"bandDown\": [");

            for (int row = 0; row < bars.size(); row++) {
                out.print(row == 0 ? "" : ",");
                out.print(numberOf(Double.isNaN(distances[row]) ? Double.NaN : edges.down()[row], 4));
            }

            out.printf("],%n  \"zones\": [%n");

            for (int at = 0; at < zones.size(); at++) {
                TrendZone zone = zones.get(at);

                out.printf(Locale.ROOT, "    {\"from\": %d, \"to\": %d, \"side\": %.0f}%s%n",
                    zone.from(), zone.to(), zone.side(), at == zones.size() - 1 ? "" : ",");
            }

            out.printf("  ]%n}%n");
        }
    }

    private static String numberOf(double value, int digits) {
        return Double.isNaN(value) ? "null" : String.format(Locale.ROOT, "%." + digits + "f", value);
    }
}
