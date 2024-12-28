package investtech.broker.contract.service.order.request;

import investtech.broker.contract.value.quotation.Quotation;

public class ReplaceOrderRequest {
    protected String accountId;
    protected String orderId;
    protected String idempotencyKey;
    protected long quantity;
    protected Quotation price;
    protected PriceType priceType;

    public String getAccountId() {
        return accountId;
    }

    public ReplaceOrderRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public String getOrderId() {
        return orderId;
    }

    public ReplaceOrderRequest setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public ReplaceOrderRequest setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    public long getQuantity() {
        return quantity;
    }

    public ReplaceOrderRequest setQuantity(long quantity) {
        this.quantity = quantity;
        return this;
    }

    public Quotation getPrice() {
        return price;
    }

    public ReplaceOrderRequest setPrice(Quotation price) {
        this.price = price;
        return this;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public ReplaceOrderRequest setPriceType(PriceType priceType) {
        this.priceType = priceType;
        return this;
    }

    public enum TimeInForceType {
        UNSPECIFIED,
        DAY,
        FILL_AND_KILL,
        FILL_OR_KIL
    }
}
