package com.siberalt.singularity.strategy.signal.volume;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;

import java.util.List;

public class VPTSignalSource implements SignalSource {
    private PriceExtractor priceExtractor = Candle::getTypical;

    public VPTSignalSource() {
    }

    public VPTSignalSource(PriceExtractor priceExtractor) {
        this.priceExtractor = priceExtractor;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (lastCandles.size() < 2) {
            return Signal.NEUTRAL; // Недостаточно данных для расчета
        }

        double vpt = 0;
        for (int i = 1; i < lastCandles.size(); i++) {
            Candle current = lastCandles.get(i);
            Candle previous = lastCandles.get(i - 1);
            Quotation currentPrice = priceExtractor.extract(current);
            Quotation previousPrice = priceExtractor.extract(previous);

            double priceChange = currentPrice.toDouble() - previousPrice.toDouble();
            double priceChangeRatio = priceChange / previousPrice.toDouble();
            vpt += priceChangeRatio * current.volume();
        }

        // Нормализация VPT для получения сигнала в диапазоне [-1, 1]
        double signal = Math.tanh(vpt / 1_000_000); // Масштабирование для нормализации
        return new Signal(signal, Math.abs(signal));
    }
}
