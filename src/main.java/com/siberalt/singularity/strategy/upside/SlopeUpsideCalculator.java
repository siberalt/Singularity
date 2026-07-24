package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.LinearRegression;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.List;

public class SlopeUpsideCalculator implements UpsideCalculator {
    private final int period;
    private final double minR2;    // минимальное R² для учёта сигнала (0.3–0.5)
    private PriceExtractor priceExtractor = Candle::close;

    public SlopeUpsideCalculator(int period, double minR2) {
        this.period = period;
        this.minR2 = minR2;
    }

    public SlopeUpsideCalculator(int period) {
        this.period = period;
        this.minR2 = 0.4;
    }

    public SlopeUpsideCalculator(PriceExtractor priceExtractor, double minR2, int period) {
        this.priceExtractor = priceExtractor;
        this.minR2 = minR2;
        this.period = period;
    }

    public SlopeUpsideCalculator() {
        this(14, 0.4);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < period + 1) {
            return Upside.NEUTRAL;
        }

        // Берём последние period свечей для вычисления наклона
        List<Candle> recentCandles = lastCandles.subList(lastCandles.size() - period, lastCandles.size());
        int recentCount = recentCandles.size();
        
        double[] recentX = new double[recentCount];
        double[] recentY = new double[recentCount];
        for (int i = 0; i < recentCount; i++) {
            recentX[i] = i;
            recentY[i] = priceExtractor.extract(recentCandles.get(i)).toDouble();
        }

        // Вычисляем наклон последних свечей
        LinearRegression recentReg = new LinearRegression(recentX, recentY);
        double recentSlope = recentReg.getSlope();
        double recentR2 = recentReg.getR2();

        // Если качество аппроксимации последних свечей ниже порога — сигнал нейтральный
        if (recentR2 < minR2) {
            return Upside.NEUTRAL;
        }

        // Вычисляем среднюю абсолютную дельту для всех остальных свечей
        List<Candle> otherCandles = lastCandles.subList(0, lastCandles.size() - period);
        double totalDelta = 0;
        for (int i = 1; i < otherCandles.size(); i++) {
            double prevPrice = priceExtractor.extract(otherCandles.get(i - 1)).toDouble();
            double currPrice = priceExtractor.extract(otherCandles.get(i)).toDouble();
            totalDelta += Math.abs(currPrice - prevPrice);
        }
        double avgDelta = otherCandles.size() > 1 ? totalDelta / (otherCandles.size() - 1) : 0;

        // Нормализуем наклон в [-1, 1] с помощью tanh
        // Сравниваем наклон последних свечей со средней дельтой остальных
        double signal = Math.tanh(recentSlope / avgDelta);
        // strength = R² (можно также умножить на дополнительный фактор)
        double strength = Math.min(1.0, recentR2 * 1.2);

        return new Upside(signal, strength);
    }
}
