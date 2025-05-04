package com.siberalt.singularity.entity.order;

import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.service.order.response.OrderState;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.service.order.request.PriceType;

import java.time.Instant;

public class Order {
    protected String accountId;
    protected OrderDirection direction;
    protected OrderType orderType;
    protected long lotsRequested;
    protected PriceType priceType;
    protected String idempotencyKey;
    protected Quotation requestedPrice;
    protected ExecutionStatus executionStatus;
    protected Instrument instrument;
    protected Quotation initialPrice;
    protected Quotation totalPrice;
    protected Quotation commissionPrice;
    protected Quotation totalPricePerOne;
    protected Quotation initialPricePerOne;
    protected Instant createdTime;
    protected long lotsExecuted;
    protected Instant expirationTime;
    protected Instant executedTime;
    protected Quotation instrumentPrice;
    protected String orderId;

    public Instant getExpirationTime() {
        return expirationTime;
    }

    public Order setExpirationTime(Instant expirationTime) {
        this.expirationTime = expirationTime;
        return this;
    }

    public Instant getExecutedTime() {
        return executedTime;
    }

    public Order setExecutedTime(Instant executedTime) {
        this.executedTime = executedTime;
        return this;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public Order setPriceType(PriceType priceType) {
        this.priceType = priceType;
        return this;
    }

    public String getAccountId() {
        return accountId;
    }

    public Order setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public OrderDirection getDirection() {
        return direction;
    }

    public Order setDirection(OrderDirection direction) {
        this.direction = direction;
        return this;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public Order setOrderType(OrderType orderType) {
        this.orderType = orderType;
        return this;
    }

    public Quotation getTotalPricePerOne() {
        return totalPricePerOne;
    }

    public Order setTotalPricePerOne(Quotation totalPricePerOne) {
        this.totalPricePerOne = totalPricePerOne;
        return this;
    }

    public Quotation getInitialPrice() {
        return initialPrice;
    }

    public Order setInitialPrice(Quotation initialPrice) {
        this.initialPrice = initialPrice;
        return this;
    }

    public Quotation getTotalPrice() {
        return totalPrice;
    }

    public Order setTotalPrice(Quotation totalPrice) {
        this.totalPrice = totalPrice;
        return this;
    }

    public Quotation getCommissionPrice() {
        return commissionPrice;
    }

    public Order setCommissionPrice(Quotation commissionPrice) {
        this.commissionPrice = commissionPrice;
        return this;
    }

    public Order setInstrumentPrice(Quotation instrumentPrice) {
        this.instrumentPrice = instrumentPrice;
        return this;
    }

    public Quotation getInstrumentPrice() {
        return instrumentPrice;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public Order setCreatedTime(Instant createdTime) {
        this.createdTime = createdTime;
        return this;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Order setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public Quotation getInitialPricePerOne() {
        return initialPricePerOne;
    }

    public Order setInitialPricePerOne(Quotation initialPricePerOne) {
        this.initialPricePerOne = initialPricePerOne;
        return this;
    }

    public Order setExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
        return this;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public Order setInstrument(Instrument instrument) {
        this.instrument = instrument;
        return this;
    }

    public long getLotsExecuted() {
        return lotsExecuted;
    }

    public Order setLotsExecuted(long lotsExecuted) {
        this.lotsExecuted = lotsExecuted;
        return this;
    }

    public long getLotsRequested() {
        return lotsRequested;
    }

    public Order setLotsRequested(long lotsRequested) {
        this.lotsRequested = lotsRequested;
        return this;
    }

    public String getOrderId() {
        return orderId;
    }

    public Order setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public Quotation getRequestedPrice() {
        return requestedPrice;
    }

    public Order setRequestedPrice(Quotation requestedPrice) {
        this.requestedPrice = requestedPrice;
        return this;
    }

    public OrderState getState() {
        String instrumentCurrency = getInstrument().getCurrency();

        return new OrderState()
            .setOrderId(orderId)
            .setDirection(direction)
            .setOrderType(orderType)
            .setInstrumentUid(instrument.getUid())
            .setOrderDate(createdTime)
            .setIdempotencyKey(idempotencyKey)
            .setLotsRequested(lotsRequested)
            .setLotsExecuted(lotsExecuted)
            .setInitialOrderPrice(Money.of(instrumentCurrency, initialPrice))
            .setExecutedOrderPrice(Money.of(instrumentCurrency, totalPricePerOne))
            .setExecutedCommission(Money.of(instrumentCurrency, commissionPrice))
            .setTotalPrice(Money.of(instrumentCurrency, totalPrice))
            .setInitialSecurityPrice(Money.of(instrumentCurrency, initialPricePerOne))
            .setExecutionStatus(executionStatus)
            .setCurrency(instrumentCurrency);
    }
}
