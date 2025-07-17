package com.siberalt.singularity.entity.transaction;

import com.siberalt.singularity.broker.contract.value.money.Money;

public record TransactionSpec(TransactionType type, String description, Money amount) {
    public TransactionSpec(TransactionType type, Money amount) {
        this(type, "Transaction of type " + type.name(), amount);
    }
}
