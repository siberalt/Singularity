package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public record ATRVolatilityCalculator(int period) implements VolatilityCalculator {

    public ATRVolatilityCalculator() {
        this(14);
    }

    @Override
    public double calculate(List<Candle> candles) {
        if (candles == null || candles.size() < period + 1) {
            return 0.0;
        }

        double[] tr = new double[candles.size()];
        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            double hl = curr.getHighPrice().toDouble() - curr.getLowPrice().toDouble();
            double hc = Math.abs(curr.getHighPrice().toDouble() - prev.getClosePriceAsDouble());
            double lc = Math.abs(curr.getLowPrice().toDouble() - prev.getClosePriceAsDouble());
            tr[i] = Math.max(hl, Math.max(hc, lc));
        }

        // Первое ATR – простое среднее первых period значений TR
        double sum = 0.0;
        for (int i = 1; i <= period; i++) {
            sum += tr[i];
        }
        double atr = sum / period;

        // Сглаживание по Уайлдеру для остальных значений
        for (int i = period + 1; i < tr.length; i++) {
            atr = (atr * (period - 1) + tr[i]) / period;
        }

        return atr;
    }
}
