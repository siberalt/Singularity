package investtech.broker.contract.service.order.request;

public class GetOrdersRequest {
    String accountId;

    public String getAccountId() {
        return accountId;
    }

    public GetOrdersRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public static GetOrdersRequest of(String accountId) {
        return new GetOrdersRequest().setAccountId(accountId);
    }
}
