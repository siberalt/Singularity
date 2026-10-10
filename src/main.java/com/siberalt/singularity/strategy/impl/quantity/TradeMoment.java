package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.strategy.signal.Signal;

import java.time.Instant;

/**
 * When and on what the sizing decision is being made, and what the strategy makes of the market.
 *
 * @param instrumentId what is being traded, by our own instrument id - the id its candle history is
 *                     read by, which is what every measure of recent activity here reads
 * @param at           the moment of the decision - the candle the strategy just reacted to, which
 *                     is what any measure of recent market activity has to be taken as of
 * @param signal       сигнал целиком, а не одно его число: размер читается из
 *                     {@link Signal#positionBalance()}, если источник его назвал, и из
 *                     {@link Signal#confidence()}, если нет, а {@link Signal#type()} отвечает, о чём
 *                     сигнал вообще. Отдавать сюда одно поле значило бы менять контракт при каждом
 *                     следующем способе считать размер
 */
public record TradeMoment(long instrumentId, Instant at, Signal signal) {
}
