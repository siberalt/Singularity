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
 * A fill applies the money and the position as two separate changes, in that order. Money first is
 * deliberate: if the second change fails the account is short, which is recoverable, rather than
 * holding instruments it never paid for. Making the pair genuinely atomic is the one gap left here.
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
