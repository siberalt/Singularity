package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.indicator.Ema;
import com.siberalt.singularity.strategy.indicator.Sma;
import com.siberalt.singularity.strategy.signal.ChangeAngleSignalSource;

import java.util.List;
import java.util.function.Function;

/**
 * Односторонность хода <b>кривой</b>: +1 - кривая шла вверх на каждом баре, -1 - вниз на каждом,
 * около нуля - изгибалась туда и обратно, то есть флэт.
 * <p>
 * Это {@link ChangeAngleSignalSource} применённый не к цене, а к любому ряду {@link MarketSeries} -
 * средней, EMA, VWAP, - и считается так же:
 * <pre>
 *     tg(угол) = Σ dᵢ / Σ |dᵢ|,   dᵢ = кривая[i] − кривая[i−1]
 * </pre>
 * Возвращается угол долей своего предела, {@code угол / 45°} - та же нормировка, что у угла цены,
 * чтобы порог значил в обоих местах одно и то же.
 * <p>
 * Какую кривую мерить, класс не решает: это {@link #overSma} или {@link #overEma}, или любая другая
 * лямбда. Сглаживание и чтение формы - разные вопросы, и зашитое внутрь сглаживание означало бы, что
 * сравнить две кривые одной мерой нельзя.
 *
 * <h2>Чем мерить значимость изгиба</h2>
 * Числитель здесь телескопируется: {@code Σ dᵢ = кривая[t] − кривая[t−k]}. Для <b>средней</b> это
 * не разность двух точек ряда цен, а разность двух средних по блокам - см. {@link #overSma}, - но
 * главное не в числителе.
 * <p>
 * Соблазн поставить такому числителю в знаменатель классическую ошибку разности средних
 * ({@code σ√(2/k)}) велик, и это ловушка: цены автокоррелированы, два блока - не две выборки вокруг
 * общего среднего, а два участка одной блуждающей траектории, и их разность определяется смещением
 * самого блуждания, а не разбросом внутри блоков. Такой критерий объявлял бы тренд почти всегда.
 * <p>
 * Поэтому знаменатель берётся из самих данных - {@code Σ |dᵢ|}, пройденный кривой путь. Он растёт
 * ровно тогда, когда кривая изгибается, поэтому <b>значимый изгиб - это близость угла к пределу</b>:
 * шум гонит путь вверх, не двигая чистый ход, и угол падает к нулю сам, без порога в абсолютных
 * единицах и без предположений о распределении.
 *
 * <h2>Шкала у каждой кривой своя</h2>
 * Предел ±45° общий, но держатся к нему разные ряды по-разному: сглаженная кривая разворачивается
 * редко, {@code Σ |dᵢ|} у неё много меньше, чем у цены, и угол сидит ближе к пределу. Порог,
 * выставленный по одной кривой, на другую не переносится - в том числе с угла цены на угол средней.
 *
 * <h2>Чего от этого ждать</h2>
 * Как <b>мера направления</b> - скорее всего ничего: прогон со скользящим началом показал, что парные
 * различия между крестом, наклоном EMA и MACD по линии и по гистограмме ни одно не проходит двух
 * ошибок, и пятая мера направления попадёт туда же. Ценность здесь в другом - в <b>классификации
 * режима</b> через {@link SteadySide}: деления «тренд против флэта» в этой базе не было вовсе, а
 * флэтовая ветка - единственное место, куда просится возврат к средней.
 * <p>
 * Параметров у ряда и угла два, и с порогами {@link SteadySide} их четыре. На такой сетке заблестит
 * что угодно: калибровка артефакта в этой базе - +149 ± 35 бп из чистого шума. Протокол объявляется
 * до прогона.
 */
public class TrendAngle implements MarketCoefficient {
    /** Предел угла: тот же, что у угла цены, по тому же неравенству треугольника. */
    public static final double MAX_DEGREES = ChangeAngleSignalSource.MAX_DEGREES;

    private final MarketSeries series;

    private final int bars;

    /**
     * @param series по чьим изгибам читается тренд
     * @param bars   сколько приращений ряда входит в угол; чтений нужно на одно больше
     */
    public TrendAngle(MarketSeries series, int bars) {
        if (series == null) {
            throw new IllegalArgumentException("Нужен ряд, по чьим изгибам читать тренд");
        }

        if (bars < 1) {
            throw new IllegalArgumentException(
                "Угол нужно мерить хотя бы по одному приращению, получено " + bars);
        }

        this.series = series;
        this.bars = bars;
    }

    /**
     * Тренд по изгибам простой средней - та самая «чистая дельта SMA», но с путём в знаменателе.
     * <p>
     * Почему для средней числитель содержательнее, чем для цены. Дельта цены за период сводится к
     * разности концов: что происходило внутри, в неё не входит вовсе. Дельта средней при
     * {@code bars ≤ period} сводится к
     * <pre>
     *     sma[t] − sma[t−k] = (k / N) · ( среднее последних k цен − среднее самых старых k цен )
     * </pre>
     * то есть к разности двух средних по блокам, где участвуют все цены блоков, а не две точки.
     */
    public static TrendAngle overSma(int period, int bars) {
        return overSma(period, bars, Candle::close);
    }

    public static TrendAngle overSma(int period, int bars, PriceExtractor priceExtractor) {
        Function<Candle, Double> price = priceOf(period, priceExtractor);

        return new TrendAngle(candles -> Sma.seriesOf(candles, period, price), bars);
    }

    /** Тот же угол по экспоненциальной средней: та же мера, другая кривая. */
    public static TrendAngle overEma(int period, int bars) {
        return overEma(period, bars, Candle::close);
    }

    public static TrendAngle overEma(int period, int bars, PriceExtractor priceExtractor) {
        Function<Candle, Double> price = priceOf(period, priceExtractor);

        return new TrendAngle(candles -> Ema.seriesOf(candles, period, price), bars);
    }

    @Override
    public double of(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < bars + 1) {
            return Double.NaN;
        }

        double[] readings = series.of(lastCandles);

        if (readings.length < bars + 1) {
            return Double.NaN;
        }

        double net = 0;
        double travelled = 0;

        for (int at = readings.length - bars; at < readings.length; at++) {
            double change = readings[at] - readings[at - 1];

            // NaN в окне значит, что кривая на своём периоде ещё не прогрелась; сколько ей нужно
            // свечей, здесь не известно и знать не нужно - см. контракт MarketSeries.
            if (Double.isNaN(change)) {
                return Double.NaN;
            }

            net += change;
            travelled += Math.abs(change);
        }

        if (travelled == 0) {
            return 0;
        }

        return Math.toDegrees(Math.atan(net / travelled)) / MAX_DEGREES;
    }

    /**
     * Цена кривой и проверка того, что кривую вообще можно собрать: период индикатора проверяется
     * здесь, а не внутри лямбды, иначе неверная сборка молчала бы до первого чтения.
     */
    private static Function<Candle, Double> priceOf(int period, PriceExtractor priceExtractor) {
        if (period < 1) {
            throw new IllegalArgumentException("Период кривой положителен, получено " + period);
        }

        if (priceExtractor == null) {
            throw new IllegalArgumentException("Нужно, по какой цене считать кривую");
        }

        return candle -> priceExtractor.extract(candle).toDouble();
    }
}
