package com.siberalt.singularity.entity.transaction;

import com.siberalt.singularity.broker.contract.value.money.Money;

import java.time.Instant;
import java.util.Map;

public class Transaction {
    private String id;
    private String description;
    private TransactionType type;
    private Money amount;
    private String destinationAccountId;
    private String sourceAccountId;
    Instant createdTime;
    Instant executedTime;
    private TransactionStatus status;
    private String errorMessage;
    private Map<String, String> metadata;

    public String getId() {
        return id;
    }

    public Transaction setId(String id) {
        this.id = id;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Transaction setDescription(String description) {
        this.description = description;
        return this;
    }

    public TransactionType getType() {
        return type;
    }

    public Transaction setType(TransactionType type) {
        this.type = type;
        return this;
    }

    public Money getAmount() {
        return amount;
    }

    public Transaction setAmount(Money amount) {
        this.amount = amount;
        return this;
    }

    public String getDestinationAccountId() {
        return destinationAccountId;
    }

    public Transaction setDestinationAccountId(String destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
        return this;
    }

    public String getSourceAccountId() {
        return sourceAccountId;
    }

    public Transaction setSourceAccountId(String sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
        return this;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public Transaction setCreatedTime(Instant createdTime) {
        this.createdTime = createdTime;
        return this;
    }

    public Instant getExecutedTime() {
        return executedTime;
    }

    public Transaction setExecutedTime(Instant executedTime) {
        this.executedTime = executedTime;
        return this;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Transaction setStatus(TransactionStatus status) {
        this.status = status;
        return this;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Transaction setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Transaction setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public Transaction addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = Map.of();
        }
        this.metadata.put(key, value);
        return this;
    }
}
