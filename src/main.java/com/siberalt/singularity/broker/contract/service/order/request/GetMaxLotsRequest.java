package com.siberalt.singularity.broker.contract.service.order.request;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

public record GetMaxLotsRequest(
    String accountId,
    String instrumentId,
    Quotation price
) {
    public GetMaxLotsRequest(String accountId, String instrumentId) {
        this(accountId, instrumentId, Quotation.ZERO);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String accountId;
        private String instrumentId;
        private Quotation price;

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder instrumentId(String instrumentId) {
            this.instrumentId = instrumentId;
            return this;
        }

        public Builder price(Quotation price) {
            this.price = price;
            return this;
        }

        public GetMaxLotsRequest build() {
            return new GetMaxLotsRequest(accountId, instrumentId, price);
        }
    }
}
