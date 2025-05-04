package com.siberalt.singularity.entity.order;

import java.util.List;

public interface ReadOrderRepository {
    Order get(String id);

    Order getByIdempotencyKey(String idempotencyKey);

    Order getByAccountIdAndOrderId(String accountId, String orderId);

    List<Order> getByAccountId(String accountId);

    Iterable<Order> getByInstrumentUid(String instrumentUid);
}
