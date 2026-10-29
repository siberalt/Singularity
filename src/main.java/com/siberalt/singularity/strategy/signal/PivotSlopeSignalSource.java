package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.LinearRegression;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ExtremePositions;
import com.siberalt.singularity.strategy.extreme.ZigZagExtremeLocator;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.List;

/**
 * Насколько круто идёт <b>текущая нога</b>: наклон регрессии от последнего разворота до последнего
 * бара, выраженный в волатильностях за бар.
 * <pre>
 *     сигнал = tanh( наклон / волатильность )
 * </pre>
 * Окно здесь одно, и второго нет нарочно: оно начинается там, где цена развернулась, и кончается
 * сейчас. Ни счёта баров, ни опорного отрезка.
 *
 * <h2>Почему нормировка волатильностью, а не собственным ходом окна</h2>
 * Поделить наклон на средний шаг <i>того же</i> окна напрашивается и не требует ничего извне, но так
 * <b>сокращается крутизна</b>: у прямой наклон и средний шаг равны, поэтому любая прямая - хоть
 * ползучая, хоть отвесная - даёт одно и то же число. Пологий чистый дрейф читался бы полной
 * уверенностью. К тому же это с точностью до регрессии наш же {@link ChangeAngleSignalSource}, который
 * меряет односторонность, а не крутизну.
 * <p>
 * Волатильность в знаменателе оставляет обе вещи на месте: чистый, но медленный ход даёт мало, быстрый
 * - много, а между бумагами числа остаются сравнимыми, потому что {@code наклон / волатильность}
 * безразмерно. Наклон в рублях за бар сравнивать между бумагами нельзя - это записано в
 * {@link SlopeSignalSource}, где он таким и остаётся.
 *
 * <h2>Чем это не {@link SlopeChangeSignalSource}</h2>
 * Тот меряет <b>изменение</b> наклона между двумя отрезками и делит разность на сумму их модулей. У
 * такой нормировки нет понятия «оба малы»: два ничтожных наклона разного знака дают ровно ±1. Измерено
 * на ликвидной трети - предел там выходит на 48-53% баров при нарезке по счёту и на 99-100% при якоре
 * на разворотах. Здесь знаменатель с измеряемым наклоном не связан вовсе.
 *
 * <h2>Задержка - это задержка локатора, и ничего сверх</h2>
 * Окно начинается с последнего разворота, который <b>уже подтверждён</b>. У
 * {@link ZigZagExtremeLocator} подтверждение стоит медианы в 1-7 баров в зависимости от порога отката;
 * у локатора по окрестности - ровно окрестность. Сам источник не ждёт ничего.
 *
 * <h2>Порог R² внутри, и это исключение из правила</h2>
 * Качество прямой гейтит сам источник, а не {@link FilterSignalSource} снаружи, как было бы принято в
 * этой базе. Причина в том, что при низком {@code R²} наклон не просто слаб, а <b>не определён</b>:
 * прямая сквозь дёрганую ногу существует формально, и её знак - монета. Отдавать такое число наружу в
 * надежде, что его отфильтруют, значит отдать шум, который выглядит как сигнал.
 */
public class PivotSlopeSignalSource implements SignalSource {
    public static final double DEFAULT_MIN_R2 = 0.4;

    /** Меньше трёх точек прямую проводить не по чему. */
    public static final int MIN_LEG_BARS = 3;

    private final ExtremeLocator pivots;

    private final int minLegBars;

    private final double minR2;

    private VolatilityCalculator volatilityCalculator = new ATRVolatilityCalculator();

    private PriceExtractor priceExtractor = Candle::close;

    /**
     * @param pivots чем искать разворот, от которого отсчитывается нога
     * @param minR2  ниже какого качества прямой наклон считается неопределённым
     */
    public PivotSlopeSignalSource(ExtremeLocator pivots, double minR2) {
        if (pivots == null) {
            throw new IllegalArgumentException("Нужно, чем искать разворот");
        }

        if (minR2 < 0 || minR2 > 1) {
            throw new IllegalArgumentException("R² лежит в [0, 1], получено " + minR2);
        }

        this.pivots = pivots;
        this.minLegBars = MIN_LEG_BARS;
        this.minR2 = minR2;
    }

    public PivotSlopeSignalSource(ExtremeLocator pivots) {
        this(pivots, DEFAULT_MIN_R2);
    }

    /** Разворот по откату на столько волатильностей - подтверждение дешевле, чем у окрестности. */
    public PivotSlopeSignalSource(double volatilities) {
        this(new ZigZagExtremeLocator(volatilities));
    }

    /** Чем мерить волатильность в знаменателе; по умолчанию ATR(14). */
    public PivotSlopeSignalSource setVolatilityCalculator(VolatilityCalculator volatilityCalculator) {
        if (volatilityCalculator == null) {
            throw new IllegalArgumentException("Нужно, чем мерить волатильность");
        }

        this.volatilityCalculator = volatilityCalculator;

        return this;
    }

    /** Читает ход не по закрытию, а по чему-нибудь ещё - по типичной цене, скажем. */
    public PivotSlopeSignalSource setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("Нужно, по какой цене проводить наклон");
        }

        this.priceExtractor = priceExtractor;

        return this;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < MIN_LEG_BARS) {
            return Signal.NEUTRAL;
        }

        int from = lastPivotOf(lastCandles);

        if (from < 0 || lastCandles.size() - from < minLegBars) {
            return Signal.NEUTRAL;
        }

        LinearRegression leg = fit(lastCandles, from);

        if (leg.getR2() < minR2) {
            return Signal.NEUTRAL;
        }

        double volatility = volatilityCalculator.calculate(lastCandles);

        // Неподвижное окно: делить не на что, а tanh от бесконечности вернул бы полную уверенность
        // на любом, сколь угодно пологом наклоне.
        if (!(volatility > 0)) {
            return Signal.NEUTRAL;
        }

        return new Signal(Math.tanh(leg.getSlope() / volatility), leg.getR2());
    }

    /** Номер бара последнего найденного разворота, или {@code -1}, если его нет. */
    private int lastPivotOf(List<Candle> lastCandles) {
        ExtremePositions.requireIndexed(lastCandles);

        int[] found = ExtremePositions.of(lastCandles, pivots.locate(lastCandles));

        return found.length == 0 ? -1 : found[found.length - 1];
    }

    /** Регрессия от разворота до последнего бара. Сдвиг по оси x на наклон не влияет. */
    private LinearRegression fit(List<Candle> lastCandles, int from) {
        int length = lastCandles.size() - from;
        double[] x = new double[length];
        double[] y = new double[length];

        for (int at = 0; at < length; at++) {
            x[at] = at;
            y[at] = priceExtractor.extract(lastCandles.get(from + at)).toDouble();
        }

        return new LinearRegression(x, y);
    }
}
