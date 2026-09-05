package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.util.function.Function;

/**
 * Which of a candle's four prices a search compares against.
 * <p>
 * The distinction matters for anything that asks "did the market reach this price": a candle only
 * tells us where the price opened, closed and how far it swung, so a level touched inside the bar
 * shows up in {@link #LOW} or {@link #HIGH} and nowhere else. Comparing against {@link #OPEN} answers
 * a different question - where the price stood at one instant - and misses every intrabar touch.
 */
public enum CandlePriceField {
    OPEN(Candle::open),
    CLOSE(Candle::close),
    HIGH(Candle::high),
    LOW(Candle::low);

    private final Function<Candle, Quotation> accessor;

    CandlePriceField(Function<Candle, Quotation> accessor) {
        this.accessor = accessor;
    }

    public Quotation of(Candle candle) {
        return accessor.apply(candle);
    }
}
