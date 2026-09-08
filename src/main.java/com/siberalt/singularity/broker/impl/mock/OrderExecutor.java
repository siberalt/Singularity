package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.order.Order;

/**
 * Prices a fill and carries it out: money, position and the resulting operations. Both
 * {@link MockOrderService} (filling an order as it is posted) and
 * {@link SimulatedPendingOrderHandler} (filling a parked order once the market reaches it) use all
 * of it - storing an order without filling it is {@link OrderRegistry}'s job, not this one's.
 * <p>
 * Every method takes the number of lots this particular fill covers, which is not always the whole
 * order: a bar may not have the volume to absorb it (see {@link LiquidityModel}), and then the order
 * fills across several of them. The order carries the running totals - lots executed, balance
 * change, commission - and each fill adds to them; the {@link FillQuote} carries what this one fill
 * costs.
 * <p>
 * A fill applies the money and the position as two separate changes, in that order. Money first is
 * deliberate: if the second change fails the account is short, which is recoverable, rather than
 * holding instruments it never paid for. Making the pair genuinely atomic is the one gap left here.
 */
public interface OrderExecutor {
    /**
     * What a fill of {@code lots} lots would consist of and cost, at the price the order currently
     * carries. Pure - neither the account nor the order is touched.
     */
    default FillQuote quote(Order order, long lots) {
        return quoteAt(order, lots, order.getInstrumentPrice());
    }

    /**
     * The same, at a price of the caller's choosing rather than the order's own. What a reservation
     * needs: it has to cover a fill that has not happened yet, at a price the order will only carry
     * once it has. Asking here rather than quoting a re-priced copy of the order keeps the
     * commission in the one place that knows it.
     */
    FillQuote quoteAt(Order order, long lots, Quotation price);

    /**
     * Carries out the fill the quote describes. Which way the instrument moves is the order's to
     * say, not the caller's - a separate buy and sell here bought nothing but the chance for the
     * two to drift apart, and made every caller repeat the same fork to pick between them.
     */
    void fill(Order order, long lots, FillQuote quote) throws AbstractException;
}
