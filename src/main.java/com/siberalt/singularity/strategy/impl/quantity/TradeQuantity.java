package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;

/**
 * How many lots a strategy asks for when it decides to trade.
 * <p>
 * Worth separating from the decision to trade at all, because the two answer to different things.
 * Whether to buy is about the signal; how much to buy is about what the market can absorb without
 * the order becoming the market - and a size that looked fine against a backtest with unlimited
 * liquidity is the first thing to go wrong once the simulation stops pretending.
 * <p>
 * <b>Здесь отвечают сколько, а не куда.</b> Направление - аргумент, потому что решает его вызывающий:
 * он один знает, открывается позиция или закрывается, и с какой стороны. Пока сторон было две - по
 * методу на каждую, - это различие размывалось: {@code toBuy} умножал лоты на уверенность <i>со знаком</i>,
 * а {@code toSell} рядом брал модуль, и закрытие шорта просило отрицательное число лотов, то есть не
 * просило ничего. Всплыло это только когда у закрытия впервые появилось направление, не совпадающее со
 * знаком сигнала.
 * <p>
 * Реализуется один метод, а не два. Декораторы от этого перестают писать пару переходников к одному и тому
 * же: и {@link AdvCappedQuantity}, и {@link BarVolumeCappedQuantity} внутри всё равно сводили обе стороны к
 * одной функции с {@link OrderDirection}, потому что потолок зависит от стороны по существу - покупка
 * забирает то, что держат офферы. Заодно контракт становится функциональным: тест-дубль теперь лямбда.
 * <p>
 * {@link #toBuy} и {@link #toSell} оставлены как два написания постоянного аргумента - они ничего не
 * решают, зато на стороне вызова видно, о чём спрашивают.
 *
 * @see SignalScaledQuantity   the plain answer - as much as the account allows, scaled by conviction
 * @see TargetPositionQuantity the same conviction read as a position to hold rather than a step to take
 * @see AdvCappedQuantity      either of them, held down to a share of what the instrument trades
 */
@FunctionalInterface
public interface TradeQuantity {
    /**
     * @param direction куда идёт заявка; от этого зависит, из чего считать размер - из того, что счёт
     *                  может купить, или из того, что он держит
     */
    long toTrade(TradeMoment moment, TradeCapacity capacity, OrderDirection direction);

    default long toBuy(TradeMoment moment, TradeCapacity capacity) {
        return toTrade(moment, capacity, OrderDirection.BUY);
    }

    default long toSell(TradeMoment moment, TradeCapacity capacity) {
        return toTrade(moment, capacity, OrderDirection.SELL);
    }
}
