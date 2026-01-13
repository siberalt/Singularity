package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@ThreadSafe
public class BaseExtremumLocator implements ExtremumLocator {
    private final Comparator<Candle> comparator;

    public BaseExtremumLocator(Comparator<Candle> comparator) {
        this.comparator = comparator;
    }

    @Override
    public List<Candle> locate(List<Candle> candles, CandleIndexProvider candleIndexProvider) {
        return candles.stream()
            .max(comparator)
            .stream()
            .toList();
    }

    // Factory method for maximum
    public static BaseExtremumLocator createMaxLocator() {
        return new BaseExtremumLocator(Comparator.comparing(Candle::getTypicalPriceAsDouble));
    }

    public static BaseExtremumLocator createMaxLocator(Function<Candle, Double> priceExtractor) {
        return new BaseExtremumLocator(Comparator.comparing(priceExtractor));
    }

    // Factory method for minimum
    public static BaseExtremumLocator createMinLocator() {
        return new BaseExtremumLocator(Comparator.comparing(Candle::getTypicalPriceAsDouble).reversed());
    }

    public static BaseExtremumLocator createMinLocator(Function<Candle, Double> priceExtractor) {
        return new BaseExtremumLocator(Comparator.comparing(priceExtractor).reversed());
    }
}
