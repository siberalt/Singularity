package com.siberalt.singularity.broker.contract.service.margin;

import java.time.Instant;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The level of a money market, as a function of time.
 * <p>
 * A level and not a rate, on purpose: what funding needs is how much money grew between two moments, and a
 * level answers that by division with no day count, no compounding convention and no special case for
 * weekends. A rate would have to be told which of those it meant.
 * <p>
 * It is a function rather than a table because the sources are not all tables. A money market fund's
 * candles are one; so are the central bank's key-rate steps, a yield curve read off bonds, and a flat rate
 * made up for a test. {@link #ofPrices} wraps the table case, and that is where carrying the last known
 * price forward belongs - a decision about a particular source, not something every user of a curve should
 * have to repeat.
 */
public interface RateCurve {
    /** Where the curve stands at this moment; the ratio of two readings is the growth between them. */
    double at(Instant moment);

    /** A curve that does not grow - funding then costs its spread and nothing else. */
    static RateCurve flat() {
        return moment -> 1;
    }

    /**
     * Prices by epoch millis, with the last known price carried forward.
     * <p>
     * Carrying forward rather than interpolating is what a traded series means: between two prints the last
     * price is what the instrument was worth, and inventing a value in between would make a loan repaid at
     * noon cost something the market never quoted. Before the series starts the first price stands, which
     * makes a loan wholly before the data cost nothing rather than fail - visibly wrong in one direction,
     * which is the better kind of wrong for a missing input.
     */
    static RateCurve ofPrices(NavigableMap<Long, Double> prices) {
        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException("Нужны цены инструмента денежного рынка");
        }

        NavigableMap<Long, Double> own = new TreeMap<>(prices);

        return moment -> {
            Map.Entry<Long, Double> found = own.floorEntry(moment.toEpochMilli());

            return (found == null ? own.firstEntry() : found).getValue();
        };
    }
}
