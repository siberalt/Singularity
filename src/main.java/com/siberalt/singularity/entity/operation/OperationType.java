package com.siberalt.singularity.entity.operation;

public enum OperationType {
    UNSPECIFIED,
    SERVICE_FEE,
    MARGIN_FEE,
    BUY,
    SELL_MARGIN,
    BROKER_FEE,
    BUY_MARGIN,
    SELL,
    DELIVERY_BUY,
    DELIVERY_SELL;

    public boolean isBuy() {
        return this == BUY || this == BUY_MARGIN || this == DELIVERY_BUY;
    }

    public boolean isSell() {
        return this == SELL || this == SELL_MARGIN || this == DELIVERY_SELL;
    }
}
