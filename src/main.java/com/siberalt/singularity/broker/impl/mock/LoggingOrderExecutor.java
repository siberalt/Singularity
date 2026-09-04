package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.strategy.context.Clock;

import java.util.List;
import java.util.logging.Logger;

/**
 * Logs every fill. Wraps {@link OrderExecutor} rather than {@link MockOrderService} on purpose: a
 * decorator on the service would only see {@code post()}, and would silently miss the fills a
 * {@link SimulatedPendingOrderHandler} performs later, when a limit order's market event comes due.
 */
public class LoggingOrderExecutor implements OrderExecutor {
    private static final Logger DEFAULT_LOGGER = Logger.getLogger(LoggingOrderExecutor.class.getName());

    private final OrderExecutor delegate;
    private final Clock clock;
    private final Logger logger;

    public LoggingOrderExecutor(OrderExecutor delegate, Clock clock) {
        this(delegate, clock, DEFAULT_LOGGER);
    }

    public LoggingOrderExecutor(OrderExecutor delegate, Clock clock, Logger logger) {
        this.delegate = delegate;
        this.clock = clock;
        this.logger = logger;
    }

    @Override
    public List<TransactionSpec> calculateTransactions(Order order) {
        return delegate.calculateTransactions(order);
    }

    @Override
    public void buy(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException {
        logger.info(
            String.format(
                "[%s] Buying instrument %s, Amount: %d, Instrument Price: %s, Total Price: %s, Commission: %s",
                clock.currentTime(),
                order.getInstrument().getUid(),
                order.getLotsRequested(),
                order.getInstrumentPrice(),
                order.getBalanceChange(),
                transactionSpecs
            )
        );
        delegate.buy(order, transactionSpecs);
    }

    @Override
    public void sell(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException {
        logger.info(
            String.format(
                "[%s] Selling instrument %s, Amount: %d, Instrument Price: %s, Total Price: %s, Commission: %s",
                clock.currentTime(),
                order.getInstrument().getUid(),
                order.getLotsRequested(),
                order.getInstrumentPrice(),
                order.getBalanceChange(),
                order.getExecutedCommission()
            )
        );
        delegate.sell(order, transactionSpecs);
    }
}
