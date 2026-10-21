import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleAggregator;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.strategy.indicator.Sma;
import com.siberalt.singularity.strategy.market.CrossingRegime;
import com.siberalt.singularity.strategy.market.MarketCoefficient;
import com.siberalt.singularity.strategy.market.MarketSeries;
import com.siberalt.singularity.strategy.market.SteadySide;
import com.siberalt.singularity.strategy.market.TrendAngle;
import com.siberalt.singularity.strategy.market.TrendZone;
import com.siberalt.singularity.strategy.market.TrendZones;

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
 */
public class TrendZoneSimulation {
    /** Самая ликвидная бумага базы по медианному минутному обороту - с неё начинаются все стенды. */
    private static final long INSTRUMENT_ID = 7;
    private static final Instant FROM = Instant.parse("2021-12-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-16T00:00:00Z");
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
     * Чем размечать режим. {@code true} - частотой пересечений цены со средней, {@code false} - углом
     * средней. Переключатель, а не два стенда: обе разметки должны ложиться на один и тот же ряд, иначе
     * сравнивать картинки нечем.
     */
    private static final boolean BY_CROSSINGS = true;
    private static final Path OUTPUT = Paths.get("src/main/resources/presenter/google/TrendZones.json");

    public static void main(String[] args) throws IOException {
        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );

        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepository candleRepository = new SqliteCandleRepositoryFactory().create(dbPath);
        List<Candle> bars = new CandleAggregator()
            .aggregate(candleRepository.getPeriod(INSTRUMENT_ID, FROM, TO), INTERVAL);
        int window = SMA_PERIOD + ANGLE_BARS;

        if (bars.size() <= window) {
            throw new IllegalStateException(
                "В периоде %d баров %s, а мере нужно больше %d".formatted(bars.size(), INTERVAL, window));
        }

        // Окно ровно такое, сколько мере нужно: лишние бары ответа не меняют, см. контракт TrendZones.
        List<TrendZone> zones = TrendZones.of(bars, regime(), window);
        double[] angles = anglesOf(bars, window);
        double[] crossings = crossingsOf(bars);

        write(bars, zones, angles, crossings);
        report(bars, zones);
    }

    private static MarketCoefficient regime() {
        return BY_CROSSINGS
            ? new CrossingRegime(curve(), CROSS_WINDOW, CROWD)
            : new SteadySide(angle(), DECISIVE, REMAINS);
    }

    private static TrendAngle angle() {
        return TrendAngle.overSma(SMA_PERIOD, ANGLE_BARS);
    }

    private static MarketSeries curve() {
        return candles -> Sma.seriesOf(candles, SMA_PERIOD, Candle::getCloseAsDouble);
    }

    /**
     * Сколько пересечений цены со средней попало в окрестность каждого бара - то, из чего получились
     * зоны при {@link #BY_CROSSINGS}. На картинке нужно рядом с ними по той же причине, что и угол: по
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
    private static void report(List<Candle> bars, List<TrendZone> zones) {
        System.out.printf(Locale.ROOT, "бумага %d, %d баров %s, зон %d%n",
            INSTRUMENT_ID, bars.size(), INTERVAL, zones.size());
        System.out.printf(Locale.ROOT, "режим: %s%n", BY_CROSSINGS
            ? "CrossingRegime(SMA(%d), окрестность %d, гроздь от %d)"
                .formatted(SMA_PERIOD, CROSS_WINDOW, CROWD)
            : "SteadySide(TrendAngle.overSma(%d, %d), %.2f, %.2f)"
                .formatted(SMA_PERIOD, ANGLE_BARS, DECISIVE, REMAINS));
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
                              double[] crossings) throws IOException {
        double[] sma = Sma.seriesOf(bars, SMA_PERIOD, Candle::getCloseAsDouble);

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(OUTPUT))) {
            out.printf(Locale.ROOT, "{%n  \"instrument\": %d,%n  \"interval\": \"%s\",%n",
                INSTRUMENT_ID, INTERVAL);
            out.printf(Locale.ROOT, "  \"settings\": {\"sma\": %d, \"bars\": %d, \"decisive\": %s,"
                    + " \"remains\": %s, \"window\": %d, \"crowd\": %d, \"byCrossings\": %s},%n",
                SMA_PERIOD, ANGLE_BARS, DECISIVE, REMAINS, CROSS_WINDOW, CROWD, BY_CROSSINGS);

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
