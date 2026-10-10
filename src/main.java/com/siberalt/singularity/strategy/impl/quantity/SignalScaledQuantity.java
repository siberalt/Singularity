package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;

/**
 * As much as the account allows, scaled by how strong the signal is: a full-conviction buy commits
 * the whole balance, a full-conviction sell closes the whole position.
 * <p>
 * The default, and the behaviour every backtest here was written against. It says nothing about
 * whether the market could absorb the result - against a thin instrument a balance-sized order is
 * many hours of that instrument's entire volume, which is a fact about the order that this sizing
 * has no way of noticing. {@link AdvCappedQuantity} wraps it to put a ceiling on that.
 * <p>
 * It is also incremental: it says how much to add now, and says the same thing again on the next
 * bar the signal is still above the threshold. {@link TargetPositionQuantity} reads the same signal
 * as a position to hold instead, which stops a lasting signal from buying the whole of a move.
 */
public class SignalScaledQuantity implements TradeQuantity {
    /**
     * {@inheritDoc}
     * <p>
     * Покупка меряется тем, что счёт может купить, продажа - тем, что он держит; это и есть вся разница
     * между сторонами здесь. Доля берётся по модулю: отвечают сколько, а куда - дело вызывающего. Со
     * знаком выходило, что закрытие шорта просит отрицательное число лотов, то есть не просит ничего.
     */
    @Override
    public long toTrade(TradeMoment moment, TradeCapacity capacity, OrderDirection direction) {
        long available = direction.isBuy() ? capacity.affordableLots() : capacity.positionLots();

        return (long) (available * Math.abs(shareOf(moment)));
    }

    /**
     * Какую долю брать: названную сигналом, а если он её не назвал - его уверенность.
     * <p>
     * Откат на уверенность и есть то, чем она была здесь всегда: {@link
     * com.siberalt.singularity.strategy.signal.Signal#confidence()} до появления
     * {@link com.siberalt.singularity.strategy.signal.Signal#positionBalance()} работала и знаком, и
     * порогом, и размером сразу. Теперь размер можно назвать отдельно, а старое поведение остаётся
     * поведением по умолчанию, так что ни один существующий источник от этого не меняется.
     * <p>
     * Доля здесь - <b>сколько добавить сейчас</b>, а не сколько держать: это приростный сайзинг, и в
     * этом он отличается от {@link TargetPositionQuantity}, который читает то же число как цель.
     */
    private static double shareOf(TradeMoment moment) {
        return moment.signal().hasPositionBalance()
            ? moment.signal().positionBalance() : moment.signal().confidence();
    }
}
