package investtech.broker.contract.service.order.request;

public class CancelOrderRequest {
    protected String accountId;
    protected String orderId;

    public String getAccountId() {
        return accountId;
    }

    public CancelOrderRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public String getOrderId() {
        return orderId;
    }

    public CancelOrderRequest setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }
}
