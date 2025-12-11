package com.siberalt.singularity.strategy.upside.volume;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.ArrayList;
import java.util.List;

public class MFIUpsideCalculator implements UpsideCalculator {
    private static final double MINIMUM_FLOW_THRESHOLD = 1e-10;
    private PriceExtractor priceExtractor = Candle::getTypicalPrice;

    public MFIUpsideCalculator() {
    }

    public MFIUpsideCalculator(PriceExtractor priceExtractor) {
        this.priceExtractor = priceExtractor;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles.size() < 2) {
            return new Upside(0, 0); // Недостаточно данных для расчета
        }

        List<Double> positiveFlow = new ArrayList<>();
        List<Double> negativeFlow = new ArrayList<>();

        for (int i = 1; i < lastCandles.size(); i++) {
            Candle current = lastCandles.get(i);
            Candle previous = lastCandles.get(i - 1);
            Quotation currentPrice = priceExtractor.extract(current);
            Quotation previousPrice = priceExtractor.extract(previous);

            double moneyFlow = currentPrice.toDouble() * current.getVolume();
            double priceDifference = currentPrice.toDouble() - previousPrice.toDouble();

            if (priceDifference > 0) {
                positiveFlow.add(moneyFlow);
            } else if (priceDifference < 0) {
                negativeFlow.add(moneyFlow);
            }
        }

        // Если нет ни положительных, ни отрицательных изменений
        if (positiveFlow.isEmpty() && negativeFlow.isEmpty()) {
            return new Upside(0, 0);
        }

        // Суммы за период
        double sumPositive = positiveFlow.stream().mapToDouble(Double::doubleValue).sum();
        double sumNegative = negativeFlow.stream().mapToDouble(Double::doubleValue).sum();

        // Проверка на слишком маленькие значения
        boolean isPositiveNegligible = Math.abs(sumPositive) < MINIMUM_FLOW_THRESHOLD;
        boolean isNegativeNegligible = Math.abs(sumNegative) < MINIMUM_FLOW_THRESHOLD;

        // Если оба потока пренебрежимо малы
        if (isPositiveNegligible && isNegativeNegligible) {
            return new Upside(0, 0);
        }

        // Если положительный поток пренебрежимо мал
        if (isPositiveNegligible) {
            return new Upside(-1.0, 1.0);
        }

        // Если отрицательный поток пренебрежимо мал
        if (isNegativeNegligible) {
            return new Upside(1.0, 1.0);
        }

        double moneyRatio = sumPositive / sumNegative;
        double mfi = 100 - (100 / (1 + moneyRatio));
        double signal = (mfi - 50) / -50; // Преобразование [0,100] в [-1,1]

        // Конвертация MFI [0,100] в сигнал [-1,1]
        return new Upside(signal, Math.abs(signal));
    }
}
