package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;

public class FindPriceParams {
    protected String instrumentUid;
    protected Instant from;
    protected Instant to;
    protected Quotation price;
    protected ComparisonOperator comparisonOperator;
    protected int maxCount;

    public int getMaxCount() {
        return maxCount;
    }

    public FindPriceParams setMaxCount(int maxCount) {
        this.maxCount = maxCount;
        return this;
    }

    public ComparisonOperator getComparisonOperator() {
        return comparisonOperator;
    }

    public FindPriceParams setComparisonOperator(ComparisonOperator comparisonOperator) {
        this.comparisonOperator = comparisonOperator;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public FindPriceParams setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public Instant getFrom() {
        return from;
    }

    public FindPriceParams setFrom(Instant from) {
        this.from = from;
        return this;
    }

    public Instant getTo() {
        return to;
    }

    public FindPriceParams setTo(Instant to) {
        this.to = to;
        return this;
    }

    public Quotation getPrice() {
        return price;
    }

    public FindPriceParams setPrice(Quotation price) {
        this.price = price;
        return this;
    }
}
