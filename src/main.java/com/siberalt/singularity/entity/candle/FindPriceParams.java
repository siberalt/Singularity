package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;

/**
 * A search for candles, within a time range, whose {@code priceField} stands in
 * {@code comparisonOperator} relation to {@code price} - at most {@code maxCount} of them, in time
 * order.
 * <p>
 * Which instrument is searched is not part of it: every other query names its instrument as its own
 * argument, and a search is no different. It also means the same criteria can be put to an
 * instrument by whatever id the asker knows it by - the candle store by ours, a broker by its own.
 */
public record FindPriceParams(
    Instant from,
    Instant to,
    Quotation price,
    CandlePriceField priceField,
    ComparisonOperator comparisonOperator,
    int maxCount
) {
    /**
     * Searches by the candle's open price, which is the answer to "where did the price stand at
     * this moment". A search for the market having reached a level has to name {@link
     * CandlePriceField#LOW} or {@link CandlePriceField#HIGH} explicitly - an open price misses
     * every touch that happened inside the bar.
     */
    public FindPriceParams(
        Instant from,
        Instant to,
        Quotation price,
        ComparisonOperator comparisonOperator,
        int maxCount
    ) {
        this(from, to, price, CandlePriceField.OPEN, comparisonOperator, maxCount);
    }

    public FindPriceParams withRange(Instant from, Instant to) {
        return new FindPriceParams(from, to, price, priceField, comparisonOperator, maxCount);
    }
}
