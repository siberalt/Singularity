package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.entity.candle.CandlePriceField;
import com.siberalt.singularity.entity.candle.ComparisonOperator;

/**
 * What a minute has to show before a parked limit order counts as filled in it.
 * <p>
 * A candle records only where the price went, not who traded there, so every choice here is an
 * assumption about the book, and they differ in how much they trust the extremes of a bar:
 * <ul>
 *   <li>{@link #TOUCH} - the market reached the limit. The most generous: a buy parked at the very low
 *       of the minute is filled, though at the low there may have been one lot traded, and not ours.</li>
 *   <li>{@link #THROUGH} - the market traded past the limit. Sound by price priority: a trade below a
 *       buy's limit can only happen once every bid at the limit and above is gone, ours included. It
 *       still trusts a single print, and a minute's low is often one.</li>
 *   <li>{@link #CLOSE_THROUGH} - the minute closed past the limit. The price did not just spike through
 *       and come back; it stayed there. Measured on RSI entries, a third of what {@code THROUGH} fills
 *       were such spikes, and they were its best fills - bought at the very tip of a wick. A minute that
 *       opens past the limit and closes back is missed, which errs on the safe side.</li>
 * </ul>
 * The fill price is the same under all three - the limit, or the open if the minute gapped past it.
 */
public enum LimitTrigger {
    TOUCH(ComparisonOperator.LESS_OR_EQUAL, ComparisonOperator.MORE_OR_EQUAL, false),
    THROUGH(ComparisonOperator.LESS, ComparisonOperator.MORE, false),
    CLOSE_THROUGH(ComparisonOperator.LESS, ComparisonOperator.MORE, true);

    private final ComparisonOperator buy;
    private final ComparisonOperator sell;
    private final boolean onClose;

    LimitTrigger(ComparisonOperator buy, ComparisonOperator sell, boolean onClose) {
        this.buy = buy;
        this.sell = sell;
        this.onClose = onClose;
    }

    /** Which price of the minute is compared with the limit. */
    public CandlePriceField field(OrderDirection direction) {
        if (onClose) {
            return CandlePriceField.CLOSE;
        }

        return direction == OrderDirection.BUY ? CandlePriceField.LOW : CandlePriceField.HIGH;
    }

    /** How that price has to stand against the limit. */
    public ComparisonOperator operator(OrderDirection direction) {
        return direction == OrderDirection.BUY ? buy : sell;
    }
}
