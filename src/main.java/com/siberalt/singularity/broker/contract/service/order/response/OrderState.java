package com.siberalt.singularity.broker.contract.service.order.response;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.entity.transaction.TransactionSpec;

import java.time.Instant;
import java.util.List;

public class OrderState {
    protected String orderId;
    protected ExecutionStatus executionStatus;
    protected long lotsRequested;
    protected long lotsExecuted;
    protected Money initialOrderPrice;
    protected Money executedOrderPrice;
    protected Money balanceChange;
    protected Money averagePositionPrice;
    protected OrderDirection direction;
    protected Money initialSecurityPrice;
    protected List<OrderStage> stages;
    protected Money serviceCommission;
    protected String currency;
    protected OrderType orderType;
    protected Instant orderDate;
    protected String instrumentUid;
    protected String idempotencyKey;
    protected List<TransactionSpec> transactions;

    public String getOrderId() {
        return orderId;
    }

    public OrderState setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public OrderState setExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
        return this;
    }

    public long getLotsRequested() {
        return lotsRequested;
    }

    public OrderState setLotsRequested(long lotsRequested) {
        this.lotsRequested = lotsRequested;
        return this;
    }

    public long getLotsExecuted() {
        return lotsExecuted;
    }

    public OrderState setLotsExecuted(long lotsExecuted) {
        this.lotsExecuted = lotsExecuted;
        return this;
    }

    public Money getInitialOrderPrice() {
        return initialOrderPrice;
    }

    public OrderState setInitialOrderPrice(Money initialOrderPrice) {
        this.initialOrderPrice = initialOrderPrice;
        return this;
    }

    public Money getExecutedOrderPrice() {
        return executedOrderPrice;
    }

    public OrderState setExecutedOrderPrice(Money executedOrderPrice) {
        this.executedOrderPrice = executedOrderPrice;
        return this;
    }

    public Money getBalanceChange() {
        return balanceChange;
    }

    public OrderState setBalanceChange(Money balanceChange) {
        this.balanceChange = balanceChange;
        return this;
    }

    public Money getAveragePositionPrice() {
        return averagePositionPrice;
    }

    public OrderState setAveragePositionPrice(Money averagePositionPrice) {
        this.averagePositionPrice = averagePositionPrice;
        return this;
    }

    public OrderDirection getDirection() {
        return direction;
    }

    public OrderState setDirection(OrderDirection direction) {
        this.direction = direction;
        return this;
    }

    public Money getInitialSecurityPrice() {
        return initialSecurityPrice;
    }

    public OrderState setInitialSecurityPrice(Money initialSecurityPrice) {
        this.initialSecurityPrice = initialSecurityPrice;
        return this;
    }

    public List<OrderStage> getStages() {
        return stages;
    }

    public OrderState setStages(List<OrderStage> stages) {
        this.stages = stages;
        return this;
    }

    public Money getServiceCommission() {
        return serviceCommission;
    }

    public OrderState setServiceCommission(Money serviceCommission) {
        this.serviceCommission = serviceCommission;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public OrderState setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public OrderState setOrderType(OrderType orderType) {
        this.orderType = orderType;
        return this;
    }

    public Instant getOrderDate() {
        return orderDate;
    }

    public OrderState setOrderDate(Instant orderDate) {
        this.orderDate = orderDate;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public OrderState setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public OrderState setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public List<TransactionSpec> getTransactions() {
        return transactions;
    }

    public OrderState setTransactions(List<TransactionSpec> transactions) {
        this.transactions = transactions;
        return this;
    }

    @Override
    public String toString() {
        var elements = List.of(
            String.format("orderId: %s", orderId),
            String.format("executionStatus: %s", executionStatus),
            String.format("lotsRequested: %d", lotsRequested),
            String.format("lotsExecuted: %d", lotsExecuted),
            String.format("initialOrderPrice: %s", initialOrderPrice),
            String.format("executedOrderPrice: %s", executedOrderPrice),
            String.format("totalOrderAmount: %s", balanceChange),
            String.format("averagePositionPrice: %s", averagePositionPrice),
            String.format("direction: %s", direction),
            String.format("initialSecurityPrice: %s", initialSecurityPrice),
            String.format("stages: %s", stages),
            String.format("serviceCommission: %s", serviceCommission),
            String.format("currency: %s", currency),
            String.format("orderType: %s", orderType),
            String.format("orderDate: %s", orderDate),
            String.format("instrumentUid: %s", instrumentUid),
            String.format("orderRequestId: %s", idempotencyKey),
            String.format("charges: %s", transactions)
        );

        return String.join("\n", elements);
    }
}
