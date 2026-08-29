package com.siberalt.singularity.entity.operation;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;

public record Operation(
    String id,
    String accountId,
    String instrumentUid,
    OperationType direction,
    long quantity,
    long quantityDone,
    Quotation price,
    Quotation payment,
    OperationState state,
    Instant date,
    Instant executedDate
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String accountId;
        private String instrumentUid;
        private OperationType direction;
        private long quantity;
        private long quantityDone;
        private Quotation price;
        private Quotation payment;
        private OperationState state;
        private Instant date;
        private Instant executedDate;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder instrumentUid(String instrumentUid) {
            this.instrumentUid = instrumentUid;
            return this;
        }

        public Builder direction(OperationType direction) {
            this.direction = direction;
            return this;
        }

        public Builder quantity(long quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder quantityDone(long quantityDone) {
            this.quantityDone = quantityDone;
            return this;
        }

        public Builder price(Quotation price) {
            this.price = price;
            return this;
        }

        public Builder payment(Quotation payment) {
            this.payment = payment;
            return this;
        }

        public Builder state(OperationState state) {
            this.state = state;
            return this;
        }

        public Builder date(Instant date) {
            this.date = date;
            return this;
        }

        public Builder executedDate(Instant executedDate) {
            this.executedDate = executedDate;
            return this;
        }

        public Operation build() {
            return new Operation(
                id,
                accountId,
                instrumentUid,
                direction,
                quantity,
                quantityDone,
                price,
                payment,
                state,
                date,
                executedDate
            );
        }
    }
}
