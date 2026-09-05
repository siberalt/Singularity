package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.FindPriceParams;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The market data an order fill needs, which is not what a client asks a broker for.
 * {@code MarketDataService} answers questions a strategy has - give me candles, give me the last
 * price; this answers the ones execution has - what is this order priced against right now, when
 * does the market reach a level, where does the data end.
 * <p>
 * It exists because {@link MockOrderService} and {@link SimulatedPendingOrderHandler} were reaching
 * into {@link MockMarketDataService}'s own methods, which held together only because the classes
 * share a package. That made a contract they all depend on look like an implementation detail free
 * to change - and the fill logic reads market data in ways that are anything but obvious, so it is
 * worth spelling out.
 */
public interface SimulationMarketData {
    /**
     * The candle covering the current moment, which is what an order posted now is priced against,
     * or {@code null} when the instrument has no data at this point of the simulation.
     */
    Candle currentCandle(String instrumentUid);

    /**
     * The last candle at or before {@code at}. Used to find where an instrument's data ends, so an
     * order that never reaches its price can be scheduled to expire against a moment that exists.
     */
    Optional<Candle> lastCandleAtOrBefore(String instrumentUid, Instant at);

    /**
     * The first candle at or after {@code at}. What an order with no price condition waits for -
     * the next moment there is a market to trade against at all.
     */
    Optional<Candle> nextCandleAtOrAfter(String instrumentUid, Instant at);

    /**
     * Candles matching a price condition, oldest first, aggregated to {@code interval}. Which of
     * the candle's prices the condition compares is part of the question - see
     * {@link com.siberalt.singularity.entity.candle.CandlePriceField}.
     */
    List<Candle> findByPrice(CandleInterval interval, FindPriceParams params);
}
