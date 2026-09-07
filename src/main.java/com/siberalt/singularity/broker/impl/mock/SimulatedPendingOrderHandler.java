package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.exception.MockBrokerException;
import com.siberalt.singularity.broker.impl.mock.shared.order.OrderEvent;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandlePriceField;
import com.siberalt.singularity.entity.candle.ComparisonOperator;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.position.Position;
import com.siberalt.singularity.simulation.Event;
import com.siberalt.singularity.simulation.EventInvoker;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.TimeDependentUnit;
import com.siberalt.singularity.strategy.context.Clock;

import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Fills orders that have to wait for the market, by looking ahead in the candle history instead of
 * re-checking on every tick: the first candle in which the market reaches the limit price becomes a
 * scheduled event, and the order is filled when the simulation reaches it. An order whose price is
 * never reached within its lifetime is scheduled as rejected instead.
 * <p>
 * An order also gets here when the market had the price but not the volume for all of it - see
 * {@link LiquidityModel}. Then it is resolved by a chain of events rather than one, each filling
 * what its own bar could absorb, until the order is complete or its lifetime runs out. An order
 * without a limit price waits only for the next bar, not for a price.
 */
public class SimulatedPendingOrderHandler implements PendingOrderHandler, EventInvoker, TimeDependentUnit {
    private static final Logger logger = Logger.getLogger(SimulatedPendingOrderHandler.class.getName());

    protected final OrderExecutor orderExecutor;
    protected final OrderRegistry orderRegistry;
    protected final OrderPriceModel priceModel;
    protected final SimulationMarketData marketDataService;
    protected final MockOperationsService operationsService;
    protected final LiquidityModel liquidityModel;
    protected final Map<String, OrderEvent> orderEvents = new HashMap<>();
    protected final Map<Instant, List<OrderEvent>> orderEventsByTime = new HashMap<>();
    protected Duration limitOrderLifeTime = Duration.ofDays(1);
    protected EventObserver eventObserver;
    protected Clock clock;

    public SimulatedPendingOrderHandler(
        Clock clock,
        OrderExecutor orderExecutor,
        OrderRegistry orderRegistry,
        OrderPriceModel priceModel,
        SimulationMarketData marketDataService,
        MockOperationsService operationsService,
        LiquidityModel liquidityModel
    ) {
        this.clock = clock;
        this.orderExecutor = orderExecutor;
        this.orderRegistry = orderRegistry;
        this.priceModel = priceModel;
        this.marketDataService = marketDataService;
        this.operationsService = operationsService;
        this.liquidityModel = liquidityModel;
    }

    public Duration getLimitOrderLifeTime() {
        return limitOrderLifeTime;
    }

    public SimulatedPendingOrderHandler setLimitOrderLifeTime(Duration limitOrderLifeTime) {
        this.limitOrderLifeTime = limitOrderLifeTime;
        return this;
    }

    /**
     * The simulator's clock is authoritative once a run starts. It is expected to be the same clock
     * the broker was built with - scheduling decisions made here and the timestamps the executor
     * stamps on fills have to agree.
     */
    @Override
    public void applyClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    /**
     * Takes the order over: nothing of it has traded, so it is stored as new and left waiting for
     * the first moment from {@code tradableFrom} on that the market can take it.
     */
    @Override
    public void onPending(Order order, Instant tradableFrom) throws AbstractException {
        order
            .setBalanceChange(Quotation.ZERO)
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.NEW);

        schedule(order, tradableFrom);
        orderRegistry.register(order);
    }

    /**
     * The lots that did fill are settled and journalled already; what is left goes back to waiting
     * for the market, on the same terms and within the order's original lifetime. The search starts
     * just past the current moment - the bar that just filled has given this order all it is going
     * to.
     */
    @Override
    public void onPartiallyFilled(Order order) throws AbstractException {
        schedule(order, clock.currentTime().plusMillis(1));
    }

    /**
     * Works out when the rest of this order can fill and books an event for that moment, reserving
     * what it will need in the meantime.
     *
     * @param searchFrom the earliest moment a fill may happen at
     */
    protected void schedule(Order order, Instant searchFrom) throws AbstractException {
        long lotsLeft = lotsLeft(order);
        Instant expiration = expirationOf(order);

        logger.info(
            String.format(
                "[%s] Predicting market event for %d lot(s) of order %s, expiring %s",
                clock.currentTime(),
                lotsLeft,
                order.getId(),
                expiration
            )
        );

        Candle signalBar = findMarketSignal(order, searchFrom, expiration);

        Order futureOrder = futureOrderOf(order);
        Instant eventTime;

        if (signalBar != null) {
            eventTime = signalBar.getTime();
            // Neither the size of the fill nor its price is settled here. Both depend on what is
            // left of that bar once the orders ahead of this one have taken their share, and that
            // is only known when the moment arrives.
        } else {
            Candle lastCandle = marketDataService
                .lastCandleAtOrBefore(order.getInstrument().getUid(), expiration)
                .orElse(null);

            if (lastCandle == null) {
                throw new MockBrokerException("Simulation error: last candle is null. Market data has ran out");
            }

            eventTime = lastCandle.getTime();

            // The moment this order would have stopped at can already be behind us: its lifetime
            // ran out while it was filling, so the last bar it could have traded on is in the past.
            // Booking an event there would drag the whole simulation back to it - the simulator
            // always jumps to the earliest event it holds, without checking it is not history - so
            // the order stops here and now instead. This moment itself is still fair game: an event
            // booked on it is picked up by the tick already in progress, or by the next one.
            if (eventTime.isBefore(clock.currentTime())) {
                stopWaiting(order);

                return;
            }

            futureOrder.setExecutionStatus(stoppedStatusOf(order));
        }

        Event event = Event.create(eventTime, this);
        OrderEvent orderEvent = new OrderEvent(futureOrder, event, signalBar);

        // Reserved against the whole of what is still working, not against the next fill alone.
        // Holding back only one bar's worth would leave the money for the rest of the order looking
        // free, and a strategy sizing its next order by the balance would commit it twice over.
        if (!blockFunds(order, lotsLeft, signalBar, orderEvent)) {
            // The account was checked against this order once, at the price it was posted at. A
            // fill at a dearer price leaves less behind than the rest of the order now costs, and
            // an order sized to the whole balance hits this the moment the market ticks up.
            if (order.getLotsExecuted() == 0) {
                // Nothing has traded, so this is simply an order the account cannot carry - the
                // same answer the caller would have got had it been unaffordable from the start.
                throw ExceptionBuilder.create(ErrorCode.INSUFFICIENT_BALANCE);
            }

            // Part of it did trade, and that stands. Refusing now would undo none of it and would
            // take the whole simulation down with it; the order stops working instead, exactly as
            // it would had the market simply never come back.
            stopWaiting(order);

            return;
        }

        orderEvents.put(futureOrder.getId(), orderEvent);
        orderEventsByTime.computeIfAbsent(eventTime, k -> new ArrayList<>()).add(orderEvent);

        eventObserver.scheduleEvent(event);
    }

    /**
     * The first bar within the window that reaches the order's price and traded enough to be worth
     * waiting for. A bar that reaches the price but is too thin to give anyone a single lot is
     * skipped rather than treated as a fill of nothing - the order is still waiting, just not here.
     */
    protected Candle findMarketSignal(Order order, Instant from, Instant to) {
        Instant searchFrom = from;

        while (!searchFrom.isAfter(to)) {
            Candle candle = priceModel.isLimit(order)
                ? findMarketSignalCandle(
                    order.getDirection(),
                    order.getRequestedPrice(),
                    order.getInstrument().getUid(),
                    searchFrom,
                    to
                )
                : marketDataService.nextCandleAtOrAfter(order.getInstrument().getUid(), searchFrom).orElse(null);

            // Outside the window there is nothing left to trade against. The lower bound matters as
            // much as the upper one: a bar at or before the search start is one this order has
            // already been given everything from, and taking it again would set the order to fill
            // on a moment it is standing on - forever.
            if (candle == null || candle.getTime().isBefore(searchFrom) || candle.getTime().isAfter(to)) {
                return null;
            }

            // Whether this bar has anything at all to trade against. How much of it is still going
            // when the order actually gets there is not knowable now - other orders will have drawn
            // on the same bar in the meantime - so that is settled at the fill itself.
            if (liquidityModel.barCapacity(candle) > 0) {
                return candle;
            }

            searchFrom = candle.getTime().plusMillis(1);
        }

        return null;
    }

    /**
     * The moment this order stops waiting, fixed once and carried on the order so that filling part
     * of it does not hand the remainder a fresh lifetime.
     */
    protected Instant expirationOf(Order order) {
        if (order.getExpirationTime() == null) {
            order.setExpirationTime(clock.currentTime().plus(limitOrderLifeTime));
        }

        return order.getExpirationTime();
    }

    protected long lotsLeft(Order order) {
        return order.getLotsRequested() - order.getLotsExecuted();
    }

    /**
     * The order as it will stand when the event comes due. It carries the same id and key - it
     * replaces the stored order rather than living beside it - and the totals filled so far, so the
     * fill adds to them instead of starting over.
     */
    protected Order futureOrderOf(Order order) {
        return new Order()
            .setAccountId(order.getAccountId())
            .setId(order.getId())
            .setIdempotencyKey(order.getIdempotencyKey())
            .setDirection(order.getDirection())
            .setOrderType(order.getOrderType())
            .setLotsRequested(order.getLotsRequested())
            .setLotsExecuted(order.getLotsExecuted())
            .setBalanceChange(order.getBalanceChange())
            .setExecutedCommission(order.getExecutedCommission())
            .setRequestedPrice(order.getRequestedPrice())
            .setExpirationTime(order.getExpirationTime())
            .setCreatedTime(order.getCreatedTime() != null ? order.getCreatedTime() : clock.currentTime())
            .setInstrument(order.getInstrument())
            .setInstrumentPrice(order.getInstrumentPrice());
    }

    /**
     * Holds back what the waiting order will need, so the same money or the same lots cannot be
     * promised to a second order while this one waits. Only for the lots actually still waiting -
     * the part that already filled is paid for and must not be reserved again.
     *
     * @return whether the account could cover it. False leaves the account untouched: it is the
     *         caller's to decide whether an order that cannot be carried any further is refused or
     *         simply stops.
     */
    protected boolean blockFunds(Order order, long lots, Candle signalBar, OrderEvent orderEvent) throws AbstractException {
        if (order.getDirection() == OrderDirection.BUY) {
            String currencyIso = order.getInstrument().getCurrency();
            Quotation needed = orderExecutor.quote(reservationPriced(order, signalBar), lots).cost();
            Quotation available = operationsService
                .getAvailableMoney(order.getAccountId(), currencyIso)
                .getQuotation();

            // Set aside what the rest of the order is expected to cost, or everything the account
            // has left if that is less. Falling short of the estimate is not a refusal: the price
            // it is estimated at is a guess, the fill itself is capped by what can actually be
            // paid, and an order that can still afford part of its remainder goes on working.
            Quotation reserved = needed.isGreaterThan(available) ? available : needed;

            if (!reserved.isGreaterThan(Quotation.ZERO)) {
                return false;
            }

            Money money = Money.of(currencyIso, reserved);
            operationsService.blockMoney(order.getAccountId(), money);
            orderEvent.setBlockedMoney(money);

            return true;
        }

        long reservedLots = lots * order.getInstrument().getLot();

        if (freeLots(order) < reservedLots) {
            return false;
        }

        operationsService.blockPosition(order.getAccountId(), order.getInstrument().getUid(), reservedLots);
        orderEvent.setBlockedLots(reservedLots);

        return true;
    }

    protected long freeLots(Order order) throws AbstractException {
        Position position = operationsService.getPositionByInstrumentId(
            order.getAccountId(),
            order.getInstrument().getUid()
        );

        return position == null ? 0 : position.getBalance();
    }

    /**
     * Ends the order where it stands, with whatever it managed to fill. Nothing is reserved and no
     * event is booked - it is simply no longer working, and the journal says so.
     */
    protected void stopWaiting(Order order) throws AbstractException {
        logger.info(
            String.format(
                "[%s] Order %s stops with %d of %d lot(s) filled",
                clock.currentTime(),
                order.getId(),
                order.getLotsExecuted(),
                order.getLotsRequested()
            )
        );

        order.setExecutionStatus(stoppedStatusOf(order));
        orderRegistry.register(order);
    }

    /**
     * How an order that will never fill any further is stored. An order that got some of what it
     * asked for was not refused - it traded, and what traded stands; what stops is the part still
     * working, which is a cancellation. Storing it as PARTIALLYFILL instead would be read as an
     * order still in the market, and it would sit among the account's working orders for good.
     */
    protected ExecutionStatus stoppedStatusOf(Order order) {
        return order.getLotsExecuted() > 0 ? ExecutionStatus.CANCELLED : ExecutionStatus.REJECTED;
    }

    /**
     * The order priced at the dearest a fill of it could plausibly be, which is what the reservation
     * has to cover. Three candidates, and the highest wins:
     * <ul>
     *   <li>what the order last traded at, which is all there is to go on for the part of it beyond
     *       the next bar;</li>
     *   <li>what the bar it is booked for is expected to charge - known here, and the reason a
     *       reservation made against a stale price used to fall short of the very next fill;</li>
     *   <li>its limit price, which a buy can be asked for once a fill has moved the order's own
     *       price down below it.</li>
     * </ul>
     * Only buys reserve money, so this is about them; a sell holds back lots, and lots do not move
     * in price.
     */
    protected Order reservationPriced(Order order, Candle signalBar) {
        if (order.getDirection() != OrderDirection.BUY) {
            return order;
        }

        Quotation price = order.getInstrumentPrice();

        if (signalBar != null) {
            price = higherOf(price, priceModel.fillPrice(order, signalBar, lotsLeft(order)));
        }

        if (priceModel.isLimit(order)) {
            price = higherOf(price, order.getRequestedPrice());
        }

        return price.isEqual(order.getInstrumentPrice())
            ? order
            : futureOrderOf(order).setInstrumentPrice(price);
    }

    protected Quotation higherOf(Quotation left, Quotation right) {
        return right.isGreaterThan(left) ? right : left;
    }

    /**
     * Trims a buy to what the account can actually pay for at the price this bar charges.
     * <p>
     * What was set aside for the order is an estimate made when it was booked; the bar it lands on
     * decides the real price, and a market that ticked up in between leaves the reservation a little
     * short. A real account cannot be made to overdraw, so the fill gives way rather than the
     * balance: the order takes what it can afford here and the rest keeps working - or stops, if it
     * can no longer afford even one lot. Selling costs nothing, so it is never trimmed.
     */
    protected long affordableLots(Order order, long lots) throws AbstractException {
        if (order.getDirection() != OrderDirection.BUY) {
            return lots;
        }

        Quotation perLot = orderExecutor.quote(order, 1).cost();

        if (!perLot.isGreaterThan(Quotation.ZERO)) {
            return lots;
        }

        Quotation available = operationsService
            .getAvailableMoney(order.getAccountId(), order.getInstrument().getCurrency())
            .getQuotation();

        // Rounded down: a fraction of a lot buys nothing, and rounding up is how the balance went
        // negative in the first place.
        long affordable = available.divide(perLot)
            .toBigDecimal()
            .setScale(0, RoundingMode.DOWN)
            .longValue();

        return Math.max(0, Math.min(lots, affordable));
    }

    protected void unblockFunds(OrderEvent orderEvent) throws AbstractException {
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
     * Drops the fill scheduled for this order. Marking it cancelled and journalling the cancel
     * operation has already been done by the caller - this only undoes the scheduling.
     */
    @Override
    public void onCancelled(Order order) throws AbstractException {
        // An order that filled immediately never got a scheduled event.
        OrderEvent orderEvent = orderEvents.remove(order.getId());

        if (orderEvent == null) {
            return;
        }

        unblockFunds(orderEvent);
        eventObserver.cancelEvent(orderEvent.getEvent());
        orderEventsByTime.computeIfPresent(
            orderEvent.getEvent().getTimePoint(),
            (time, events) -> {
                events.remove(orderEvent);
                return events.isEmpty() ? null : events;
            }
        );
    }

    @Override
    public void tick() {
        var currentTime = clock.currentTime();
        List<OrderEvent> dueEvents = orderEventsByTime.remove(currentTime);

        if (dueEvents == null) {
            return;
        }

        // Acting on an event can book another one, and that one can land on this very moment - a
        // remainder that finds no further bar stops right here. The due events are taken off the
        // map above rather than read from it, so scheduling into this same moment builds a fresh
        // list that this loop is not walking; whatever appears there is drained in below, since the
        // simulator will not visit this timestamp again.
        Deque<OrderEvent> queue = new ArrayDeque<>(dueEvents);

        while (!queue.isEmpty()) {
            OrderEvent orderEvent = queue.poll();
            logger.info(
                String.format("[%s] Executing order event: %s", currentTime, orderEvent.getEvent().getId())
            );

            Order order = orderEvent.getOrder();
            // The event is consumed here either way, so drop it from the by-id index too -
            // otherwise it accumulates for the whole simulation.
            orderEvents.remove(order.getId());

            try {
                // Whatever was held back for this order goes back to the account before it either
                // fills - paying out of the freed funds - or expires unfilled.
                unblockFunds(orderEvent);

                if (!orderEvent.fills()) {
                    logger.info(
                        String.format(
                            "[%s] Order %s stops waiting with %d of %d lot(s) filled",
                            currentTime,
                            order.getId(),
                            order.getLotsExecuted(),
                            order.getLotsRequested()
                        )
                    );
                    orderRegistry.register(order);

                    continue;
                }

                Candle bar = orderEvent.getBar();
                long lots = liquidityModel.take(order.getInstrument().getUid(), bar, lotsLeft(order));

                if (lots == 0) {
                    // The orders ahead of this one took the whole bar. Nothing trades here, so the
                    // order simply goes back to waiting for the next one.
                    logger.info(
                        String.format(
                            "[%s] Order %s found the bar used up, waiting for the next",
                            currentTime,
                            order.getId()
                        )
                    );
                    onPartiallyFilled(order);

                    continue;
                }

                order.setInstrumentPrice(priceModel.fillPrice(order, bar, lots));
                lots = affordableLots(order, lots);

                if (lots == 0) {
                    // Whatever was set aside no longer covers a single lot at what this bar
                    // charges. Nothing more of this order can trade, so it ends here.
                    stopWaiting(order);

                    continue;
                }

                FillQuote quote = orderExecutor.quote(order, lots);

                if (order.getDirection() == OrderDirection.BUY) {
                    orderExecutor.buy(order, lots, quote);
                } else {
                    orderExecutor.sell(order, lots, quote);
                }

                // The bar gave what it had; if the order still wants more it goes back to waiting.
                if (lotsLeft(order) > 0) {
                    onPartiallyFilled(order);
                }
            } catch (AbstractException e) {
                logger.severe(
                    String.format(
                        "[%s] Error while executing order: %s",
                        currentTime,
                        orderEvent.getEvent().getId()
                    )
                );
                throw new MockBrokerException("Error while executing order", e);
            }

            List<OrderEvent> scheduledNow = orderEventsByTime.remove(currentTime);

            if (scheduledNow != null) {
                queue.addAll(scheduledNow);
            }
        }
    }

    /**
     * The first candle in which the market reaches the order's limit price, or {@code null} if it
     * never does within the window.
     * <p>
     * A parked buy is waiting for the price to come down to its limit, so it triggers on the first
     * candle whose low touches the limit; a parked sell waits for the price to come up, and
     * triggers on a high. Comparing against the candle's open instead would ask a different
     * question - where the price happened to stand at one instant - and would miss every limit the
     * market crossed inside a bar.
     */
    protected Candle findMarketSignalCandle(
        OrderDirection orderDirection,
        Quotation requestedPrice,
        String instrumentUid,
        Instant from,
        Instant to
    ) {
        boolean isBuy = orderDirection == OrderDirection.BUY;

        return marketDataService.findByPrice(
                CandleInterval.MIN_1,
                new FindPriceParams(
                    instrumentUid,
                    from,
                    to,
                    requestedPrice,
                    isBuy ? CandlePriceField.LOW : CandlePriceField.HIGH,
                    isBuy ? ComparisonOperator.LESS_OR_EQUAL : ComparisonOperator.MORE_OR_EQUAL,
                    1
                )
            )
            .stream()
            .findFirst()
            .orElse(null);
    }
}
