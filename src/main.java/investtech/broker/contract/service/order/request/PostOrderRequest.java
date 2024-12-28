package investtech.broker.contract.service.order.request;

import investtech.broker.contract.value.quotation.Quotation;

public class PostOrderRequest {
    protected long quantity;
    protected Quotation price;
    protected OrderDirection direction;
    protected String accountId;
    protected OrderType orderType;
    protected String orderId;
    protected String instrumentId;
    protected ReplaceOrderRequest.TimeInForceType timeInForce;
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

    public String getOrderId() {
        return orderId;
    }

    public PostOrderRequest setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public PostOrderRequest setInstrumentId(String instrumentId) {
        this.instrumentId = instrumentId;
        return this;
    }

    public ReplaceOrderRequest.TimeInForceType getTimeInForce() {
        return timeInForce;
    }

    public PostOrderRequest setTimeInForce(ReplaceOrderRequest.TimeInForceType timeInForce) {
        this.timeInForce = timeInForce;
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
