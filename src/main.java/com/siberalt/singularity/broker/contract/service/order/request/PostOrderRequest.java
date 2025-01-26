package com.siberalt.singularity.broker.contract.service.order.request;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

public class PostOrderRequest {
    protected long quantity;
    protected Quotation price;
    protected OrderDirection direction;
    protected String accountId;
    protected OrderType orderType;
    protected String idempotencyKey;
    protected String instrumentId;
    protected PriceType priceType;

    public long getQuantity() {
        return quantity;
    }

    public PostOrderRequest setQuantity(long quantity) {
        this.quantity = quantity;
        return this;
    }

    public Quotation getPrice() {
        return price;
    }

    public PostOrderRequest setPrice(Quotation price) {
        this.price = price;
        return this;
    }

    public OrderDirection getDirection() {
        return direction;
    }

    public PostOrderRequest setDirection(OrderDirection direction) {
        this.direction = direction;
        return this;
    }

    public String getAccountId() {
        return accountId;
    }

    public PostOrderRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public PostOrderRequest setOrderType(OrderType orderType) {
        this.orderType = orderType;
        return this;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PostOrderRequest setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public PostOrderRequest setInstrumentId(String instrumentId) {
        this.instrumentId = instrumentId;
        return this;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public PostOrderRequest setPriceType(PriceType priceType) {
        this.priceType = priceType;
        return this;
    }
}
