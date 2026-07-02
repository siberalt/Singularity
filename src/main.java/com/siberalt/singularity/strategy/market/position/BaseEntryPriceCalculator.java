package com.siberalt.singularity.strategy.market.position;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.ReadOrderRepository;
import com.siberalt.singularity.shared.TimePointRange;

import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BaseEntryPriceCalculator implements EntryPriceCalculator {
    private final ReadOrderRepository orderRepository;

    private static final ConcurrentHashMap<String, SoftReference<CachedEntry>> cache = new ConcurrentHashMap<>();

    private record CachedEntry(EntryPrice state, Instant lastProcessedTime) {
    }

    public BaseEntryPriceCalculator(ReadOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public EntryPrice calculate(String accountId, String instrumentUid) {
        String key = buildKey(accountId, instrumentUid);

        SoftReference<CachedEntry> ref = cache.get(key);
        CachedEntry cached = (ref != null) ? ref.get() : null;
        List<Order> orders;
        EntryPrice state;
        EntryPrice initialState;

        if (cached != null) {
            orders = orderRepository.getByAccountIdAndInstrumentUidAfterTime(
                accountId, instrumentUid, cached.lastProcessedTime()
            );
            initialState = cached.state();
        } else {
            orders = orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid);
            initialState = EntryPrice.EMPTY;
        }

        orders = orders.stream()
            .filter(Order::isFilled)
            .filter(o -> o.getExecutedTime() != null) // защита от null
            .sorted(Comparator.comparing(Order::getExecutedTime))
            .toList();

        if (orders.isEmpty()) {
            return initialState;
        }

        state = aggregateOrders(initialState, orders);

        Instant lastTime = orders.getLast().getExecutedTime();
        cache.put(key, new SoftReference<>(new CachedEntry(state, lastTime)));
        return state;
    }

    private EntryPrice aggregateOrders(EntryPrice previous, List<Order> orders) {
        EntryPrice state = previous;

        for (Order order : orders) {
            state = applyOrder(state, order);
        }

        return state;
    }

    private EntryPrice applyOrder(EntryPrice previous, Order order) {
        long previousQuantity = previous.quantity();
        double previousPrice = previous.averagePrice().toDouble();

        double orderPrice = order.getInstrumentPrice().toDouble();
        long orderLots = order.getLotsExecuted();
        boolean isBuy = order.getDirection().isBuy();

        if (previousQuantity == 0) {
            return new EntryPrice(
                isBuy ? orderLots : -orderLots,
                Quotation.of(orderPrice),
                new TimePointRange(new TimePoint(order.getExecutedTime()))
            );
        }

        if ((previousQuantity > 0 && isBuy) || (previousQuantity < 0 && !isBuy)) {
            long oldVolume = Math.abs(previousQuantity);
            long newVolume = oldVolume + orderLots;
            double newAvg = (previousPrice * oldVolume + orderPrice * orderLots) / newVolume;

            return new EntryPrice(
                previousQuantity > 0 ? newVolume : -newVolume,
                Quotation.of(newAvg),
                TimePointRange.unionByInstants(
                    previous.timePointRange(),
                    new TimePointRange(new TimePoint(order.getExecutedTime()))
                )
            );
        } else {
            long oppositeVolume = Math.abs(previousQuantity);
            if (orderLots > oppositeVolume) {
                long remaining = orderLots - oppositeVolume;

                return new EntryPrice(
                    isBuy ? remaining : -remaining,
                    Quotation.of(orderPrice),
                    new TimePointRange(new TimePoint(order.getExecutedTime()))
                );
            } else if (orderLots < oppositeVolume) {
                long newQty = previousQuantity + (isBuy ? orderLots : -orderLots);

                return new EntryPrice(newQty, Quotation.of(previousPrice), previous.timePointRange());
            } else {
                return EntryPrice.EMPTY;
            }
        }
    }

    private String buildKey(String accountId, String instrumentUid) {
        return accountId + "|" + instrumentUid;
    }

    public static void invalidate(String accountId, String instrumentUid) {
        cache.remove(accountId + "|" + instrumentUid);
    }

    public static void clearCache() {
        cache.clear();
    }
}
