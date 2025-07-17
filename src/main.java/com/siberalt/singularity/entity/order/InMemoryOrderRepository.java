package com.siberalt.singularity.entity.order;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryOrderRepository implements OrderRepository {
    private final Map<String, Order> ordersById = new ConcurrentHashMap<>();
    private final Map<String, Order> ordersByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public Order get(String id) {
        return ordersById.get(id);
    }

    @Override
    public Order getByIdempotencyKey(String idempotencyKey) {
        return ordersByIdempotencyKey.get(idempotencyKey);
    }

    @Override
    public Order getByAccountIdAndOrderId(String accountId, String orderId) {
        return ordersById.values().stream()
            .filter(order -> order.getAccountId().equals(accountId) && order.getId().equals(orderId))
            .findFirst()
            .orElse(null);
    }

    @Override
    public List<Order> getByAccountId(String accountId) {
        return ordersById.values().stream()
            .filter(order -> order.getAccountId().equals(accountId))
            .collect(Collectors.toList());
    }

    @Override
    public Iterable<Order> getByAccountIdAndInstrumentUid(String accountId, String instrumentUid) {
        return ordersById.values().stream()
            .filter(
                order -> order.getInstrument().getUid().equals(instrumentUid) && order.getAccountId().equals(accountId)
            )
            .collect(Collectors.toList());
    }

    @Override
    public void save(Order order) {
        ordersById.put(order.getId(), order);
        ordersByIdempotencyKey.put(order.getIdempotencyKey(), order);
    }

    @Override
    public void delete(Order order) {
        ordersById.remove(order.getId());
        ordersByIdempotencyKey.remove(order.getIdempotencyKey());
    }

    @Override
    public void deleteById(Long id) {
        Order order = ordersById.remove(id.toString());
        if (order != null) {
            ordersByIdempotencyKey.remove(order.getIdempotencyKey());
        }
    }
}
