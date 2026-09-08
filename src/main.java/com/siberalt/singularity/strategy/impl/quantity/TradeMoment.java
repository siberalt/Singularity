package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.strategy.upside.Upside;

import java.time.Instant;

/**
 * When and on what the sizing decision is being made, and what the strategy makes of the market.
 *
 * @param instrumentId what is being traded
 * @param at           the moment of the decision - the candle the strategy just reacted to, which
 *                     is what any measure of recent market activity has to be taken as of
 * @param upside       the calculation the decision rests on, whole. Not just its signal: how much
 *                     to trade is exactly the question that wants {@link Upside#strength()} as well,
 *                     and a sizing that wanted to weigh conviction against confidence should not
 *                     have to be given a wider contract first
 */
public record TradeMoment(String instrumentId, Instant at, Upside upside) {
}
