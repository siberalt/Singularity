package com.siberalt.singularity.broker.contract.service.order;

import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.entity.order.Order;

import java.util.Optional;

public interface TransactionSpecProvider {
    Optional<TransactionSpec> provide(Order order);
}
