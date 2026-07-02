package com.siberalt.singularity.entity.order;

import java.time.Instant;
import java.util.List;

public interface ReadOrderRepository {
    Order get(String id);

    Order getByIdempotencyKey(String idempotencyKey);

    Order getByAccountIdAndOrderId(String accountId, String orderId);

    List<Order> getByAccountId(String accountId);

    List<Order> getByAccountIdAndInstrumentUid(String accountId, String instrumentUid);

    List<Order> getByAccountIdAndInstrumentUidAfterTime(String accountId, String instrumentUid, Instant after);
}
