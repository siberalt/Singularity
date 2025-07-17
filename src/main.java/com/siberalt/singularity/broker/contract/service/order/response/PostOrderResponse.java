package com.siberalt.singularity.broker.contract.service.order.response;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.entity.transaction.Transaction;

import java.util.List;

public class PostOrderResponse {
    protected String orderId;
    protected ExecutionStatus executionStatus;
    protected long lotsRequested;
    protected long lotsExecuted;
    protected Money totalBalanceChange;
    protected Money aciValue;
    protected OrderDirection direction;
    protected Money instrumentPrice;
    protected OrderType orderType;
    protected String message;
    protected String instrumentUid;
    protected String idempotencyKey;
    protected List<Transaction> transactions;

    public String getOrderId() {
        return orderId;
    }

    public PostOrderResponse setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public PostOrderResponse setExecutionStatus(ExecutionStatus executionReportStatus) {
        this.executionStatus = executionReportStatus;
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

    public Money getTotalBalanceChange() {
        return totalBalanceChange;
    }

    public PostOrderResponse setTotalBalanceChange(Money totalBalanceChange) {
        this.totalBalanceChange = totalBalanceChange;
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

    public Money getInstrumentPrice() {
        return instrumentPrice;
    }

    public PostOrderResponse setInstrumentPrice(Money instrumentPrice) {
        this.instrumentPrice = instrumentPrice;
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

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public PostOrderResponse setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
        return this;
    }
}
