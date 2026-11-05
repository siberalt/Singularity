package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.List;

/**
 * Режим по расстоянию цены от кривой в волатильностях: {@link #UP}, когда цена ушла над кривой дальше
 * полосы, {@link #DOWN}, когда под неё, а внутри полосы - прежняя сторона.
 *
 * <h2>Чем это лучше пересечения с подтверждением</h2>
 * {@link DecisiveCrossing} меняет сторону на событии - пересечении, - и если событие не подтвердилось,
 * прежняя сторона может держаться сколько угодно: медленный рост над средней не набирает решительного
 * наклона никогда. Здесь залипание <b>ограничено</b>: дальше {@code width} волатильностей от кривой
 * сторона определена однозначно, без всяких условий.
 * <p>
 * И дрейф кривой отсекается сам, без наклона. Пока цена стоит, средняя подходит к ней монотонно и не
 * перехлёстывает - её окно заполняется той же ценой, - так что расстояние стремится к нулю с той же
 * стороны и до противоположного края полосы не доходит. «Средняя всплыла над стоящей ценой» полосу не
 * пересекает по построению.
 *
 * <h2>Цена полосы</h2>
 * Каждое переключение запаздывает на ход в {@code width} волатильностей от кривой, каждый ложный
 * пробой стоит около двух ширин. Это то же обменное отношение лага на дребезг, что у любого
 * гистерезиса, но с явной и постоянной ценой. Шкала в волатильностях переносится между бумагами: по
 * ликвидной трети дневными барами медиана {@code |цена − SMA(80)| / ATR(14)} вышла 2.48, а у отдельных
 * бумаг от 1.88 до 2.83.
 *
 * <h2>Контроль - та же сборка</h2>
 * При {@code width == 0} полоса схлопывается, и ответ - сторона, с которой цена стоит, ровно как у
 * {@link DecisiveCrossing#naive}. Сравнивать полосу надо с ней самой в нулевой ширине.
 *
 * <h2>Состояния между вызовами нет</h2>
 * Сторона пересчитывается проходом по окну, как в {@link CrossingRegime}. Первый читаемый бар её
 * засевает по знаку расстояния, поэтому мёртвого участка сверх прогрева кривой нет; если за всё окно
 * цена ни разу не вышла из полосы, ответ - этот знак, то есть память ограничена окном.
 * <p>
 * Волатильность берётся у каждого бара через {@link VolatilityCalculator#profile}, а не одна на окно:
 * окно длинное, и мерить расстояние годовой давности сегодняшним ATR незачем.
 */
public class DistanceBand implements MarketCoefficient {
    public static final double UP = TrendZone.UP;

    public static final double DOWN = TrendZone.DOWN;

    private final MarketSeries level;

    private final double width;

    private PriceExtractor priceExtractor = Candle::close;

    private VolatilityCalculator volatilityCalculator = new ATRVolatilityCalculator();

    /**
     * @param level кривая, расстояние до которой меряется; обычно средняя
     * @param width полуширина полосы в волатильностях; ноль даёт сторону цены без гистерезиса
     */
    public DistanceBand(MarketSeries level, double width) {
        if (level == null) {
            throw new IllegalArgumentException("Нужна кривая, расстояние до которой мерить");
        }

        if (width < 0 || Double.isNaN(width)) {
            throw new IllegalArgumentException("Ширина полосы неотрицательна, получено " + width);
        }

        this.level = level;
        this.width = width;
    }

    /** Мерит от кривой не закрытие, а что-нибудь ещё - типичную цену, скажем. */
    public DistanceBand setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("Нужно, какую цену сравнивать с кривой");
        }

        this.priceExtractor = priceExtractor;

        return this;
    }

    public DistanceBand setVolatilityCalculator(VolatilityCalculator volatilityCalculator) {
        if (volatilityCalculator == null) {
            throw new IllegalArgumentException("Нужно, чем мерить шкалу расстояния");
        }

        this.volatilityCalculator = volatilityCalculator;

        return this;
    }

    @Override
    public double of(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Double.NaN;
        }

        double[] distances = distancesOf(lastCandles);
        double side = 0;

        for (double distance : distances) {
            if (Double.isNaN(distance)) {
                continue;
            }

            if (distance > width) {
                side = UP;
            } else if (distance < -width) {
                side = DOWN;
            } else if (side == 0) {
                // Засев по знаку: внутри полосы прежней стороны ещё нет, а ровно на кривой нет и знака.
                side = Math.signum(distance);
            }
        }

        return side == 0 ? Double.NaN : side;
    }

    /**
     * Расстояние цены от кривой в волатильностях у каждого бара окна; {@link Double#NaN}, где мерить
     * нечем - кривая не прогрелась или волатильность не положительна.
     */
    public double[] distancesOf(List<Candle> lastCandles) {
        double[] curve = level.of(lastCandles);
        double[] volatility = volatilityCalculator.profile(lastCandles);
        double[] distances = new double[lastCandles.size()];

        for (int at = 0; at < distances.length; at++) {
            boolean measurable = at < curve.length && at < volatility.length
                && !Double.isNaN(curve[at]) && volatility[at] > 0;

            distances[at] = measurable
                ? (priceExtractor.extract(lastCandles.get(at)).toDouble() - curve[at]) / volatility[at]
                : Double.NaN;
        }

        return distances;
    }
}
