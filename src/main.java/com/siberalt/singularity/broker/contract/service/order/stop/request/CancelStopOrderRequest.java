package com.siberalt.singularity.broker.contract.service.order.stop.request;

public class CancelStopOrderRequest {
    protected String accountId;
    protected String stopOrderId;

    public String getAccountId() {
        return accountId;
    }

    public CancelStopOrderRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public String getStopOrderId() {
        return stopOrderId;
    }

    public CancelStopOrderRequest setStopOrderId(String stopOrderId) {
        this.stopOrderId = stopOrderId;
        return this;
    }
}
