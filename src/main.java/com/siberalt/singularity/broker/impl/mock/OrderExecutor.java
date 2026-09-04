package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.transaction.TransactionSpec;

import java.util.List;

/**
 * Prices a fill and carries it out: money, position and the resulting operations. Both
 * {@link MockOrderService} (filling an order as it is posted) and
 * {@link SimulatedPendingOrderHandler} (filling a parked order once the market reaches it) use all
 * of it - storing an order without filling it is {@link OrderRegistry}'s job, not this one's.
 * <p>
 * This is also the single place a future implementation would make a fill transactional: right now
 * a transaction rejected halfway through leaves the earlier ones applied.
 */
public interface OrderExecutor {
    /**
     * Returns the transactions the fill consists of and records the resulting balance change and
     * commission on the order. Pure with respect to the account - nothing is applied until
     * {@link #buy} or {@link #sell}.
     */
    List<TransactionSpec> calculateTransactions(Order order);

    void buy(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException;

    void sell(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException;
}
