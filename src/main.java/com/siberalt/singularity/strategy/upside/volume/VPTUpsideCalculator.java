package com.siberalt.singularity.strategy.upside.volume;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;

public class VPTUpsideCalculator implements UpsideCalculator {
    private PriceExtractor priceExtractor = Candle::getTypicalPrice;

    public VPTUpsideCalculator() {
    }

    public VPTUpsideCalculator(PriceExtractor priceExtractor) {
        this.priceExtractor = priceExtractor;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles.size() < 2) {
            return new Upside(0, 0); // Недостаточно данных для расчета
        }

        double vpt = 0;
        for (int i = 1; i < lastCandles.size(); i++) {
            Candle current = lastCandles.get(i);
            Candle previous = lastCandles.get(i - 1);
            Quotation currentPrice = priceExtractor.extract(current);
            Quotation previousPrice = priceExtractor.extract(previous);

            double priceChange = currentPrice.toDouble() - previousPrice.toDouble();
            double priceChangeRatio = priceChange / previousPrice.toDouble();
            vpt += priceChangeRatio * current.getVolume();
        }

        // Нормализация VPT для получения сигнала в диапазоне [-1, 1]
        double signal = Math.tanh(vpt / 1_000_000); // Масштабирование для нормализации
        return new Upside(signal, Math.abs(signal));
    }
}
