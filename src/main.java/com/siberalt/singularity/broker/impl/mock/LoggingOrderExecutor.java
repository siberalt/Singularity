package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.strategy.context.Clock;

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
    public FillQuote quote(Order order, long lots) {
        return delegate.quote(order, lots);
    }

    @Override
    public void buy(Order order, long lots, FillQuote quote) throws AbstractException {
        log("Buying", order, lots, quote);
        delegate.buy(order, lots, quote);
    }

    @Override
    public void sell(Order order, long lots, FillQuote quote) throws AbstractException {
        log("Selling", order, lots, quote);
        delegate.sell(order, lots, quote);
    }

    /**
     * Says how much of the order this fill covers, not just how much it is for - an order filling
     * across several bars is otherwise indistinguishable in the log from one that keeps being
     * re-posted.
     */
    private void log(String action, Order order, long lots, FillQuote quote) {
        logger.info(
            String.format(
                "[%s] %s instrument %s, Amount: %d of %d, Instrument Price: %s, Total Price: %s, Commission: %s",
                clock.currentTime(),
                action,
                order.getInstrument().getUid(),
                lots,
                order.getLotsRequested(),
                order.getInstrumentPrice(),
                quote.balanceChange(),
                quote.commission()
            )
        );
    }
}
