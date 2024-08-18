package investtech.broker.contract.service.operation.request;

public class GetPositionsRequest {
    String accountId;

    public String getAccountId() {
        return accountId;
    }

    public GetPositionsRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public static GetPositionsRequest of(String accountId) {
        return new GetPositionsRequest().setAccountId(accountId);
    }
}
