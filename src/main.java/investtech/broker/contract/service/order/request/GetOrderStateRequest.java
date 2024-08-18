package investtech.broker.contract.service.order.request;

public class GetOrderStateRequest {
    protected String accountId;
    protected String orderId;
    protected PriceType priceType;

    public String getAccountId() {
        return accountId;
    }

    public GetOrderStateRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public String getOrderId() {
        return orderId;
    }

    public GetOrderStateRequest setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public GetOrderStateRequest setPriceType(PriceType priceType) {
        this.priceType = priceType;
        return this;
    }
}
