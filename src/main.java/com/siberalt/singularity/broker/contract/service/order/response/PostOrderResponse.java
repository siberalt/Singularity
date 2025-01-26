package com.siberalt.singularity.broker.contract.service.order.response;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;

public class PostOrderResponse {
    protected String orderId;
    protected ExecutionStatus executionReportStatus;
    protected long lotsRequested;
    protected long lotsExecuted;
    protected Money initialPrice;
    protected Money totalPricePerOne;
    protected Money totalPrice;
    protected Money initialCommission;
    protected Money executedCommission;
    protected Money aciValue;
    protected OrderDirection direction;
    protected Money initialPricePerOne;
    protected OrderType orderType;
    protected String message;
    protected String instrumentUid;
    protected String idempotencyKey;

    public String getOrderId() {
        return orderId;
    }

    public PostOrderResponse setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionReportStatus;
    }

    public PostOrderResponse setExecutionStatus(ExecutionStatus executionReportStatus) {
        this.executionReportStatus = executionReportStatus;
        return this;
    }

    public long getLotsRequested() {
        return lotsRequested;
    }

    public PostOrderResponse setLotsRequested(long lotsRequested) {
        this.lotsRequested = lotsRequested;
        return this;
    }

    public long getLotsExecuted() {
        return lotsExecuted;
    }

    public PostOrderResponse setLotsExecuted(long lotsExecuted) {
        this.lotsExecuted = lotsExecuted;
        return this;
    }

    public Money getInitialPrice() {
        return initialPrice;
    }

    public PostOrderResponse setInitialPrice(Money initialPrice) {
        this.initialPrice = initialPrice;
        return this;
    }

    public Money getTotalPricePerOne() {
        return totalPricePerOne;
    }

    public PostOrderResponse setTotalPricePerOne(Money totalPricePerOne) {
        this.totalPricePerOne = totalPricePerOne;
        return this;
    }

    public Money getTotalPrice() {
        return totalPrice;
    }

    public PostOrderResponse setTotalPrice(Money totalPrice) {
        this.totalPrice = totalPrice;
        return this;
    }

    public Money getInitialCommission() {
        return initialCommission;
    }

    public PostOrderResponse setInitialCommission(Money initialCommission) {
        this.initialCommission = initialCommission;
        return this;
    }

    public Money getExecutedCommission() {
        return executedCommission;
    }

    public PostOrderResponse setExecutedCommission(Money executedCommission) {
        this.executedCommission = executedCommission;
        return this;
    }

    public Money getAciValue() {
        return aciValue;
    }

    public PostOrderResponse setAciValue(Money aciValue) {
        this.aciValue = aciValue;
        return this;
    }

    public OrderDirection getDirection() {
        return direction;
    }

    public PostOrderResponse setDirection(OrderDirection direction) {
        this.direction = direction;
        return this;
    }

    public Money getInitialPricePerOne() {
        return initialPricePerOne;
    }

    public PostOrderResponse setInitialPricePerOne(Money initialPricePerOne) {
        this.initialPricePerOne = initialPricePerOne;
        return this;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public PostOrderResponse setOrderType(OrderType orderType) {
        this.orderType = orderType;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public PostOrderResponse setMessage(String message) {
        this.message = message;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public PostOrderResponse setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PostOrderResponse setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }
}
