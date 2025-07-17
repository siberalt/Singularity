package com.siberalt.singularity.broker.contract.service.order.request;

public enum OrderDirection {
    UNSPECIFIED,
    BUY,
    SELL;

    public boolean isBuy() {
        return this == BUY;
    }

    public boolean isSell() {
        return this == SELL;
    }
}
