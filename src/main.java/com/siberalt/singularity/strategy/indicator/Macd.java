package com.siberalt.singularity.strategy.indicator;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;
import java.util.function.Function;

/**
 * MACD: разность быстрой и медленной экспоненциальных средних, её собственная средняя и зазор между ними.
 * <p>
 * Три числа, и путать их легко, поэтому стоит назвать каждое. <b>Линия</b> - это {@code EMA(fast) -
 * EMA(slow)}, то есть насколько быстрая средняя оторвалась от медленной. <b>Сигнальная</b> - экспоненциальная
 * средняя самой линии, то есть сглаженная версия того же. <b>Гистограмма</b> - линия минус сигнальная, и
 * именно её пересечение нуля обычно и называют сигналом MACD.
 * <p>
 * Чем он отличается от пересечения двух средних, которое уже есть в {@link Sma} и
 * {@link com.siberalt.singularity.strategy.signal.trend.MovingAverageCrossSignalSource}: там сигналом
 * является сторона разрыва, здесь - сторона <b>изменения</b> разрыва. Линия выше своей сигнальной означает,
 * что отрыв быстрой средней растёт, то есть тренд ускоряется; это срабатывает раньше пересечения средних и
 * чаще ошибается. Ровно тот же обмен, что между наклоном EMA и крестом, измеренный в {@code docs/signals.md}.
 * <p>
 * Сиды унаследованы от {@link Ema}: первые {@code slow} цен дают простое среднее, и до тех пор читать нечего.
 * Сигнальная средняя начинает считаться с первой готовой линии, так что полная готовность наступает через
 * {@code slow + signal} цен. Пока её нет, все три значения - {@link Double#NaN}.
 */
public class Macd {
    public static final int DEFAULT_FAST = 12;
    public static final int DEFAULT_SLOW = 26;
    public static final int DEFAULT_SIGNAL = 9;

    private final Ema fast;
    private final Ema slow;
    private final Ema signal;
    private final Function<Candle, Double> priceExtractor;

    public Macd(int fastPeriod, int slowPeriod, int signalPeriod,
                Function<Candle, Double> priceExtractor) {
        if (fastPeriod < 1 || fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException(
                "The fast period must be positive and less than the slow one, got "
                    + fastPeriod + " and " + slowPeriod);
        }

        if (signalPeriod < 1) {
            throw new IllegalArgumentException(
                "The signal period must be positive, got " + signalPeriod);
        }

        this.fast = new Ema(fastPeriod, priceExtractor);
        this.slow = new Ema(slowPeriod, priceExtractor);
        // Сигнальная средняя считается по значениям линии, а не по ценам, поэтому экстрактор ей не нужен.
        this.signal = new Ema(signalPeriod);
        this.priceExtractor = priceExtractor;
    }

    public Macd(int fastPeriod, int slowPeriod, int signalPeriod) {
        this(fastPeriod, slowPeriod, signalPeriod, Candle::getCloseAsDouble);
    }

    /** MACD(12, 26, 9) - то, что имеют в виду, когда говорят «MACD» без уточнений. */
    public Macd() {
        this(DEFAULT_FAST, DEFAULT_SLOW, DEFAULT_SIGNAL);
    }

    public double add(Candle candle) {
        return add(priceExtractor.apply(candle));
    }

    /** Добавляет цену и возвращает гистограмму после неё. */
    public double add(double price) {
        fast.add(price);
        slow.add(price);

        if (!Double.isNaN(line())) {
            signal.add(line());
        }

        return histogram();
    }

    /** Насколько быстрая средняя оторвалась от медленной, или NaN, пока медленная не набрала период. */
    public double line() {
        return fast.ready() && slow.ready() ? fast.value() - slow.value() : Double.NaN;
    }

    /** Сглаженная линия, или NaN, пока её средняя не набрала период. */
    public double signalLine() {
        return signal.value();
    }

    /** Линия минус сигнальная: положительная - отрыв растёт, отрицательная - сокращается. */
    public double histogram() {
        return ready() ? line() - signalLine() : Double.NaN;
    }

    public boolean ready() {
        return !Double.isNaN(line()) && signal.ready();
    }

    /** Гистограмма по каждой свече - форма, которой пользуются калькуляторы и графики. */
    public static double[] seriesOf(List<Candle> candles, int fastPeriod, int slowPeriod,
                                    int signalPeriod) {
        Macd macd = new Macd(fastPeriod, slowPeriod, signalPeriod);
        double[] histogram = new double[candles.size()];

        for (int at = 0; at < candles.size(); at++) {
            macd.add(candles.get(at));
            histogram[at] = macd.histogram();
        }

        return histogram;
    }

    public static double[] seriesOf(List<Candle> candles) {
        return seriesOf(candles, DEFAULT_FAST, DEFAULT_SLOW, DEFAULT_SIGNAL);
    }

    /** Сама линия по каждой свече - отдельно от гистограммы, потому что её ноль тоже используют сигналом. */
    public static double[] lineSeriesOf(List<Candle> candles, int fastPeriod, int slowPeriod,
                                        int signalPeriod) {
        Macd macd = new Macd(fastPeriod, slowPeriod, signalPeriod);
        double[] line = new double[candles.size()];

        for (int at = 0; at < candles.size(); at++) {
            macd.add(candles.get(at));
            line[at] = macd.line();
        }

        return line;
    }
}
