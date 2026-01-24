package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@ThreadSafe
public class BaseExtremeLocator implements ExtremeLocator {
    private final Comparator<Candle> comparator;

    public BaseExtremeLocator(Comparator<Candle> comparator) {
        this.comparator = comparator;
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        return candles.stream()
            .max(comparator)
            .stream()
            .toList();
    }

    // Factory method for maximum
    public static BaseExtremeLocator createMaxLocator() {
        return new BaseExtremeLocator(Comparator.comparing(Candle::getTypicalPriceAsDouble));
    }

    public static BaseExtremeLocator createMaxLocator(Function<Candle, Double> priceExtractor) {
        return new BaseExtremeLocator(Comparator.comparing(priceExtractor));
    }

    // Factory method for minimum
    public static BaseExtremeLocator createMinLocator() {
        return new BaseExtremeLocator(Comparator.comparing(Candle::getTypicalPriceAsDouble).reversed());
    }

    public static BaseExtremeLocator createMinLocator(Function<Candle, Double> priceExtractor) {
        return new BaseExtremeLocator(Comparator.comparing(priceExtractor).reversed());
    }
}
