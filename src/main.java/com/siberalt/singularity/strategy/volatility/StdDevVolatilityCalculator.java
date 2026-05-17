package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;

import java.util.List;

public class StdDevVolatilityCalculator implements VolatilityCalculator {
    private final int period;
    private final PriceExtractor priceExtractor;

    /**
     * @param period период для расчёта стандартного отклонения
     * @param priceExtractor функция извлечения цены из свечи (например, Candle::getClose)
     */
    public StdDevVolatilityCalculator(int period, PriceExtractor priceExtractor) {
        if (period < 2) throw new IllegalArgumentException("Period must be at least 2");
        this.period = period;
        this.priceExtractor = priceExtractor;
    }

    /**
     * Удобный конструктор: использует цену закрытия.
     */
    public StdDevVolatilityCalculator(int period) {
        this(period, Candle::getClose);
    }

    @Override
    public double calculate(List<Candle> candles) {
        if (candles == null || candles.size() < period) {
            return 0.0;
        }

        // берём последние period свечей
        List<Candle> subList = candles.subList(candles.size() - period, candles.size());

        // извлекаем цены
        double[] prices = new double[period];
        for (int i = 0; i < period; i++) {
            prices[i] = priceExtractor.extract(subList.get(i)).toDouble();
        }

        // среднее арифметическое
        double sum = 0.0;
        for (double p : prices) sum += p;
        double mean = sum / period;

        // сумма квадратов отклонений
        double sumSq = 0.0;
        for (double p : prices) {
            double diff = p - mean;
            sumSq += diff * diff;
        }

        // выборочное стандартное отклонение (n-1) – общепринято в финансах
        double variance = sumSq / (period - 1);
        return Math.sqrt(variance);
    }
}
