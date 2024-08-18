package investtech.broker.contract.service.order.stop.request;

import investtech.broker.contract.service.order.stop.common.StopOrderStatusOption;

import java.time.Instant;

public class GetStopOrdersRequest {
    protected String accountId;
    protected StopOrderStatusOption status;
    protected Instant from;
    protected Instant to;

    public String getAccountId() {
        return accountId;
    }

    public GetStopOrdersRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public StopOrderStatusOption getStatus() {
        return status;
    }

    public GetStopOrdersRequest setStatus(StopOrderStatusOption status) {
        this.status = status;
        return this;
    }

    public Instant getFrom() {
        return from;
    }

    public GetStopOrdersRequest setFrom(Instant from) {
        this.from = from;
        return this;
    }

    public Instant getTo() {
        return to;
    }

    public GetStopOrdersRequest setTo(Instant to) {
        this.to = to;
        return this;
    }
}
