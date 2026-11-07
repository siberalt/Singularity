package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.LinearRegression;

import java.util.List;

/**
 * Наклон регрессии цены по всем переданным свечам, в волатильностях за бар. Окно задаёт вызывающий:
 * {@code candles.subList(at - bars, at + 1)} - это наклон по {@code bars} приращениям на баре {@code at}.
 * Сужение и отмена в {@link DistanceBand} мерят скорость им одним.
 */
final class PriceSlope {
    private PriceSlope() {
    }

    /**
     * @return наклон в волатильностях за бар; {@link Double#NaN}, если свечей меньше двух или
     *     волатильность не положительна
     */
    static double of(List<Candle> candles, PriceExtractor price, double volatility) {
        if (candles.size() < 2 || !(volatility > 0)) {
            return Double.NaN;
        }

        double[] x = new double[candles.size()];
        double[] y = new double[candles.size()];

        for (int step = 0; step < x.length; step++) {
            x[step] = step;
            y[step] = price.extract(candles.get(step)).toDouble();
        }

        return new LinearRegression(x, y).getSlope() / volatility;
    }
}
