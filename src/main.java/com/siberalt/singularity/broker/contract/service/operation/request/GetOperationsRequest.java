package com.siberalt.singularity.broker.contract.service.operation.request;

import java.time.Instant;

public class GetOperationsRequest {
    protected String accountId;
    protected Instant from;
    protected Instant to;

    public String getAccountId() {
        return accountId;
    }

    public GetOperationsRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public Instant getFrom() {
        return from;
    }

    public GetOperationsRequest setFrom(Instant from) {
        this.from = from;
        return this;
    }

    public Instant getTo() {
        return to;
    }

    public GetOperationsRequest setTo(Instant to) {
        this.to = to;
        return this;
    }
}
