package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;
import java.util.Objects;

public record InvertedUpsideCalculator(UpsideCalculator delegate) implements UpsideCalculator {
    public InvertedUpsideCalculator {
        Objects.requireNonNull(delegate, "Delegate cannot be null");
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        Upside original = delegate.calculate(lastCandles);
        return new Upside(-original.signal(), original.strength());
    }
}
