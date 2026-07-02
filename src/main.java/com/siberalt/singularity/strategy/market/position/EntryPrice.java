package com.siberalt.singularity.strategy.market.position;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.shared.TimePointRange;

public record EntryPrice(long quantity, Quotation averagePrice, TimePointRange timePointRange) {
    public static EntryPrice EMPTY = new EntryPrice(0, Quotation.ZERO, TimePointRange.EMPTY);

    public EntryPrice(long quantity, Quotation averagePrice) {
        this(quantity, averagePrice, TimePointRange.EMPTY);
    }

    public boolean isEmpty() {
        return quantity == 0;
    }
}
