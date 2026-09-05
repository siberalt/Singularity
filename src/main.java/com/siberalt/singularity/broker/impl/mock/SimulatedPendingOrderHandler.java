package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
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

    @Override
    public void onNotFillable(Order order) throws AbstractException {
        order
            .setBalanceChange(Quotation.ZERO)
            .setLotsExecuted(0)
            .setExecutionStatus(ExecutionStatus.NEW);

        // The order is new to us, so the current bar is fair game: the market may well reach its
        // price within the bar it was posted in.
        schedule(order, clock.currentTime());
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

        MarketSignal signal = findMarketSignal(order, lotsLeft, searchFrom, expiration);

        Order futureOrder = futureOrderOf(order);
        Instant eventTime;
        long lotsToFill;

        if (signal != null) {
            eventTime = signal.candle().getTime();
            lotsToFill = signal.fillableLots();
            // No status set here: this event ends in a fill, and only the executor knows how the
            // order stands once it is applied - whether the bar finished it or left more to do.

            if (order.getRequestedPrice() != null) {
                futureOrder.setInstrumentPrice(
                    priceModel.limitFillPrice(order.getDirection(), order.getRequestedPrice(), signal.candle())
                );
            } else {
                futureOrder.setInstrumentPrice(signal.candle().open());
            }
        } else {
            Candle lastCandle = marketDataService
                .lastCandleAtOrBefore(order.getInstrument().getUid(), expiration)
                .orElse(null);

            if (lastCandle == null) {
                throw new MockBrokerException("Simulation error: last candle is null. Market data has ran out");
            }

            eventTime = lastCandle.getTime();
            lotsToFill = 0;
            // Nothing will fill, so this is the status the order is stored with. An order that got
            // some of what it asked for is not a rejected order - it traded, and simply stops here
            // with whatever it managed to fill.
            futureOrder.setExecutionStatus(
                order.getLotsExecuted() > 0 ? ExecutionStatus.PARTIALLYFILL : ExecutionStatus.REJECTED
            );
        }

        Event event = Event.create(eventTime, this);
        OrderEvent orderEvent = new OrderEvent(futureOrder, event, lotsToFill);
        blockFunds(order, lotsToFill > 0 ? lotsToFill : lotsLeft, orderEvent);
        orderEvents.put(futureOrder.getId(), orderEvent);
        orderEventsByTime.computeIfAbsent(eventTime, k -> new ArrayList<>()).add(orderEvent);

        eventObserver.scheduleEvent(event);
    }

    /**
     * The first bar within the window that both reaches the order's price and has the volume to
     * give it at least one lot. A bar that reaches the price but is too thin to trade against is
     * skipped rather than treated as a fill of nothing - the order is still waiting, just not here.
     */
    protected MarketSignal findMarketSignal(Order order, long lotsLeft, Instant from, Instant to) {
        Instant searchFrom = from;

        while (!searchFrom.isAfter(to)) {
            Candle candle = order.getRequestedPrice() != null
                ? findMarketSignalCandle(
                    order.getDirection(),
                    order.getRequestedPrice(),
                    order.getInstrument().getUid(),
                    searchFrom,
                    to
                )
                : marketDataService.nextCandleAtOrAfter(order.getInstrument().getUid(), searchFrom).orElse(null);

            if (candle == null || candle.getTime().isAfter(to)) {
                return null;
            }

            long fillableLots = liquidityModel.fillableLots(lotsLeft, candle);

            if (fillableLots > 0) {
                return new MarketSignal(candle, fillableLots);
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
     * A bar the order can trade against, and how much of it that bar can absorb.
     */
    protected record MarketSignal(Candle candle, long fillableLots) {
    }

    /**
     * Holds back what the waiting order will need, so the same money or the same lots cannot be
     * promised to a second order while this one waits. Only for the lots actually still waiting -
     * the part that already filled is paid for and must not be reserved again.
     */
    protected void blockFunds(Order order, long lots, OrderEvent orderEvent) throws AbstractException {
        if (order.getDirection() == OrderDirection.BUY) {
            Money cost = Money.of(
                order.getInstrument().getCurrency(),
                orderExecutor.quote(reservationPriced(order), lots).cost()
            );
            operationsService.blockMoney(order.getAccountId(), cost);
            orderEvent.setBlockedMoney(cost);

            return;
        }

        long reservedLots = lots * order.getInstrument().getLot();
        operationsService.blockPosition(order.getAccountId(), order.getInstrument().getUid(), reservedLots);
        orderEvent.setBlockedLots(reservedLots);
    }

    /**
     * The order priced at the dearest a fill of it could plausibly be, which is what the reservation
     * has to cover: the current market when the order is first parked - above the limit it will
     * actually fill at - and the limit price itself once a fill has moved the order's price down to
     * what it last traded at. A market order has no ceiling, so the last price seen is the best
     * estimate available.
     */
    protected Order reservationPriced(Order order) {
        Quotation limitPrice = order.getRequestedPrice();

        if (limitPrice == null || order.getDirection() != OrderDirection.BUY) {
            return order;
        }

        return limitPrice.isGreaterThan(order.getInstrumentPrice())
            ? futureOrderOf(order).setInstrumentPrice(limitPrice)
            : order;
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
        // remainder that finds no further bar stops right here. Taking the due events off the map
        // and draining whatever reappears keeps those from being either missed or iterated over
        // while they are being added.
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

                if (orderEvent.getLotsToFill() == 0) {
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

                long lots = orderEvent.getLotsToFill();
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
