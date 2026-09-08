package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.exception.MockBrokerException;
import com.siberalt.singularity.broker.impl.mock.shared.order.OrderEvent;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.simulation.Event;
import com.siberalt.singularity.simulation.EventInvoker;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.TimeDependentUnit;
import com.siberalt.singularity.strategy.context.Clock;

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
    protected final LiquidityModel liquidityModel;
    protected final MarketSignalSearch marketSignalSearch;
    protected final OrderFundsReserve fundsReserve;
    protected final Map<String, OrderEvent> orderEvents = new HashMap<>();
    protected final Map<Instant, List<OrderEvent>> orderEventsByTime = new HashMap<>();
    protected Duration limitOrderLifeTime = Duration.ofDays(1);
    protected EventObserver eventObserver;
    protected Clock clock;

    /**
     * The search and the reserve are built here rather than handed in: they are this class's own
     * decomposition, not policies anyone would swap, and they are assembled from the very
     * collaborators it is already given.
     */
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
        this.liquidityModel = liquidityModel;
        this.marketSignalSearch = new MarketSignalSearch(marketDataService, priceModel, liquidityModel);
        this.fundsReserve = new OrderFundsReserve(operationsService, orderExecutor, priceModel);
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

        Candle signalBar = marketSignalSearch.nextTradableBar(order, searchFrom, expiration);

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
        if (!fundsReserve.hold(order, lotsLeft, signalBar, orderEvent)) {
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

        fundsReserve.release(orderEvent);
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

            // The event is consumed here either way, so drop it from the by-id index too -
            // otherwise it accumulates for the whole simulation.
            orderEvents.remove(orderEvent.getOrder().getId());

            try {
                resolve(orderEvent);
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
     * Acts on one event that has come due: the order either trades against its bar or stops. What
     * was held back for it goes back to the account first, since the fill is paid for out of it.
     */
    protected void resolve(OrderEvent orderEvent) throws AbstractException {
        Order order = orderEvent.getOrder();

        fundsReserve.release(orderEvent);

        if (!orderEvent.fills()) {
            logger.info(
                String.format(
                    "[%s] Order %s stops waiting with %d of %d lot(s) filled",
                    clock.currentTime(),
                    order.getId(),
                    order.getLotsExecuted(),
                    order.getLotsRequested()
                )
            );
            orderRegistry.register(order);

            return;
        }

        BarFill barFill = claimFromBar(order, orderEvent.getBar());

        switch (barFill.outcome()) {
            case BAR_USED_UP -> {
                // The orders ahead of this one took the whole bar. Nothing trades here, so the
                // order goes back to waiting for the next one.
                logger.info(
                    String.format(
                        "[%s] Order %s found the bar used up, waiting for the next",
                        clock.currentTime(),
                        order.getId()
                    )
                );
                onPartiallyFilled(order);
            }
            // Whatever was set aside no longer covers a single lot at what this bar charges.
            // Nothing more of this order can trade, so it ends here.
            case UNAFFORDABLE -> stopWaiting(order);
            case TRADES -> {
                fill(order, barFill);

                // The bar gave what it had; if the order still wants more it goes back to waiting.
                if (lotsLeft(order) > 0) {
                    onPartiallyFilled(order);
                }
            }
        }
    }

    /**
     * Takes this order's share of the bar and works out what the fill would be - the two things
     * that could not be settled when the event was booked, because the bar is shared and its price
     * is its own.
     * <p>
     * It does claim from the bar's budget, which is a change to the world and is meant to be: the
     * share taken here is a share the orders behind this one no longer have. What it deliberately
     * does not do is act on the answer - nothing is scheduled, journalled or priced onto the order.
     * Which of the three outcomes it reports is {@link #resolve}'s to act on.
     */
    protected BarFill claimFromBar(Order order, Candle bar) throws AbstractException {
        long lots = liquidityModel.take(order.getInstrument().getUid(), bar, lotsLeft(order));

        if (lots == 0) {
            return BarFill.barUsedUp();
        }

        Quotation price = priceModel.fillPrice(order, bar, lots);
        long affordable = fundsReserve.affordableLots(order, lots, price);

        return affordable == 0 ? BarFill.unaffordable() : BarFill.trades(affordable, price);
    }

    protected void fill(Order order, BarFill barFill) throws AbstractException {
        order.setInstrumentPrice(barFill.price());

        long lots = barFill.lots();
        FillQuote quote = orderExecutor.quote(order, lots);

        if (order.getDirection() == OrderDirection.BUY) {
            orderExecutor.buy(order, lots, quote);
        } else {
            orderExecutor.sell(order, lots, quote);
        }
    }

    /**
     * What one bar came to for one order: either lots at a price, or the reason there were none.
     * The two ways of getting nothing are kept apart because they are acted on differently - a bar
     * that ran out leaves the order still working, an account that cannot pay ends it.
     */
    protected record BarFill(Outcome outcome, long lots, Quotation price) {
        protected enum Outcome {
            TRADES,
            BAR_USED_UP,
            UNAFFORDABLE
        }

        protected static BarFill trades(long lots, Quotation price) {
            return new BarFill(Outcome.TRADES, lots, price);
        }

        protected static BarFill barUsedUp() {
            return new BarFill(Outcome.BAR_USED_UP, 0, null);
        }

        protected static BarFill unaffordable() {
            return new BarFill(Outcome.UNAFFORDABLE, 0, null);
        }
    }
}
