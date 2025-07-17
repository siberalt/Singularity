package com.siberalt.singularity.entity.transaction;

public enum TransactionType {
    COMMISSION("Commission"),
    INTEREST("Interest"),
    TAX("Tax"),
    FEE("Fee"),
    DIVIDEND("Dividend"),
    REBATE("Rebate"),
    OTHER("Other"),
    UNKNOWN("Unknown"),
    BUY("Buy"),
    SELL("Sell");

    private final String description;

    TransactionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
