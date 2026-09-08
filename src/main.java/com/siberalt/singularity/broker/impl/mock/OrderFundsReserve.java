package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.order.OrderEvent;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.position.Position;

import java.math.RoundingMode;

/**
 * Holds back what a waiting order will need, so the same money or the same lots cannot be promised
 * to a second order while this one waits, and hands it back when the order gets its turn.
 * <p>
 * What is set aside is an estimate. The price a fill goes through at is the bar's to decide, and it
 * is not known when the reservation is made, so the two can disagree - a market that ticks up in
 * the meantime leaves the reservation short. Which is why this also answers
 * {@link #affordableLots}: the account cannot be made to overdraw, so when estimate and reality
 * part company the fill gives way rather than the balance.
 * <p>
 * Only buys reserve money. A sell holds back lots, and lots do not move in price - what was set
 * aside is exactly what is needed.
 */
public class OrderFundsReserve {
    private final MockOperationsService operationsService;
    private final OrderExecutor orderExecutor;
    private final OrderPriceModel priceModel;

    public OrderFundsReserve(
        MockOperationsService operationsService,
        OrderExecutor orderExecutor,
        OrderPriceModel priceModel
    ) {
        this.operationsService = operationsService;
        this.orderExecutor = orderExecutor;
        this.priceModel = priceModel;
    }

    /**
     * Sets aside what {@code lots} of this order are expected to cost, recording the amount on the
     * event so it can be given back unchanged - by the time the event comes due the order carries
     * the price it actually filled at, and the figure could no longer be worked out from it.
     *
     * @param signalBar the bar the order is booked to trade on, whose price the reservation is
     *                  sized by; {@code null} when nothing is expected to fill
     * @return whether anything could be set aside at all. False means the account has nothing left
     *         to commit, and it is the caller's to decide whether that refuses the order or simply
     *         stops it
     */
    public boolean hold(Order order, long lots, Candle signalBar, OrderEvent orderEvent) throws AbstractException {
        if (order.getDirection() == OrderDirection.BUY) {
            return holdMoney(order, lots, signalBar, orderEvent);
        }

        return holdLots(order, lots, orderEvent);
    }

    public void release(OrderEvent orderEvent) throws AbstractException {
        Order order = orderEvent.getOrder();

        if (orderEvent.getBlockedMoney() != null) {
            operationsService.unblockMoney(order.getAccountId(), orderEvent.getBlockedMoney());
            orderEvent.setBlockedMoney(null);
        }

        if (orderEvent.getBlockedLots() > 0) {
            operationsService.unblockPosition(
                order.getAccountId(),
                order.getInstrument().getUid(),
                orderEvent.getBlockedLots()
            );
            orderEvent.setBlockedLots(0);
        }
    }

    /**
     * Trims a buy to what the account can actually pay for at {@code price}. Selling costs nothing,
     * so it is never trimmed.
     * <p>
     * The price is asked for rather than read off the order because the order does not carry it
     * yet: this answers whether a fill can go ahead, and only a fill that does sets the price it
     * went through at.
     */
    public long affordableLots(Order order, long lots, Quotation price) throws AbstractException {
        if (order.getDirection() != OrderDirection.BUY) {
            return lots;
        }

        Quotation perLot = orderExecutor.quoteAt(order, 1, price).cost();

        if (!perLot.isGreaterThan(Quotation.ZERO)) {
            return lots;
        }

        Quotation available = availableMoney(order);

        // Rounded down: a fraction of a lot buys nothing, and rounding up is how a balance goes
        // negative.
        long affordable = available.divide(perLot)
            .toBigDecimal()
            .setScale(0, RoundingMode.DOWN)
            .longValue();

        return Math.max(0, Math.min(lots, affordable));
    }

    /**
     * Sets aside what the rest of the order is expected to cost, or everything the account has left
     * if that is less. Falling short of the estimate is not a refusal: the price it is estimated at
     * is a guess, the fill itself is capped by {@link #affordableLots}, and an order that can still
     * afford part of its remainder goes on working.
     */
    private boolean holdMoney(
        Order order,
        long lots,
        Candle signalBar,
        OrderEvent orderEvent
    ) throws AbstractException {
        Quotation needed = orderExecutor.quoteAt(order, lots, reservationPrice(order, signalBar)).cost();
        Quotation available = availableMoney(order);
        Quotation reserved = needed.isGreaterThan(available) ? available : needed;

        if (!reserved.isGreaterThan(Quotation.ZERO)) {
            return false;
        }

        Money money = Money.of(order.getInstrument().getCurrency(), reserved);
        operationsService.blockMoney(order.getAccountId(), money);
        orderEvent.setBlockedMoney(money);

        return true;
    }

    /**
     * The lots are already the account's, and the post-time check made sure there were enough, so
     * this is a guard on an invariant rather than a case that is expected to come up. It is worth
     * having because the alternative is not a refusal but an {@code IllegalStateException} out of
     * the account balance, which would take the whole run down instead of this one order.
     */
    private boolean holdLots(Order order, long lots, OrderEvent orderEvent) throws AbstractException {
        long reservedLots = lots * order.getInstrument().getLot();

        if (freeLots(order) < reservedLots) {
            return false;
        }

        operationsService.blockPosition(order.getAccountId(), order.getInstrument().getUid(), reservedLots);
        orderEvent.setBlockedLots(reservedLots);

        return true;
    }

    /**
     * The dearest a fill could plausibly be, which is what the reservation has to cover. Three
     * candidates, and the highest wins:
     * <ul>
     *   <li>what the order last traded at, which is all there is to go on for the part of it beyond
     *       the next bar;</li>
     *   <li>what the bar it is booked for is expected to charge - known here, and the reason a
     *       reservation made against a stale price used to fall short of the very next fill;</li>
     *   <li>its limit price, which a buy can be asked for once a fill has moved the order's own
     *       price down below it.</li>
     * </ul>
     */
    private Quotation reservationPrice(Order order, Candle signalBar) {
        Quotation price = order.getInstrumentPrice();

        if (signalBar != null) {
            price = higherOf(price, priceModel.fillPrice(order, signalBar, lotsLeft(order)));
        }

        if (priceModel.isLimit(order)) {
            price = higherOf(price, order.getRequestedPrice());
        }

        return price;
    }

    private Quotation availableMoney(Order order) throws AbstractException {
        return operationsService
            .getAvailableMoney(order.getAccountId(), order.getInstrument().getCurrency())
            .getQuotation();
    }

    private long freeLots(Order order) throws AbstractException {
        Position position = operationsService.getPositionByInstrumentId(
            order.getAccountId(),
            order.getInstrument().getUid()
        );

        return position == null ? 0 : position.getBalance();
    }

    private long lotsLeft(Order order) {
        return order.getLotsRequested() - order.getLotsExecuted();
    }

    private Quotation higherOf(Quotation left, Quotation right) {
        return right.isGreaterThan(left) ? right : left;
    }
}
