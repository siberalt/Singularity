package com.siberalt.singularity.broker.contract.service.instrument.common;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;

public class Dividend {
    protected Money dividendNet;
    protected Instant paymentDate;
    protected Instant declaredDate;
    protected Instant lastBuyDate;
    protected String dividendType;
    protected Instant recordDate;
    protected String regularity;
    protected Money closePrice;
    protected Quotation yieldValue;
    protected Instant createdAt;

    public Money getDividendNet() {
        return dividendNet;
    }

    public Dividend setDividendNet(Money dividendNet) {
        this.dividendNet = dividendNet;
        return this;
    }

    public Instant getPaymentDate() {
        return paymentDate;
    }

    public Dividend setPaymentDate(Instant paymentDate) {
        this.paymentDate = paymentDate;
        return this;
    }

    public Instant getDeclaredDate() {
        return declaredDate;
    }

    public Dividend setDeclaredDate(Instant declaredDate) {
        this.declaredDate = declaredDate;
        return this;
    }

    public Instant getLastBuyDate() {
        return lastBuyDate;
    }

    public Dividend setLastBuyDate(Instant lastBuyDate) {
        this.lastBuyDate = lastBuyDate;
        return this;
    }

    public String getDividendType() {
        return dividendType;
    }

    public Dividend setDividendType(String dividendType) {
        this.dividendType = dividendType;
        return this;
    }

    public Instant getRecordDate() {
        return recordDate;
    }

    public Dividend setRecordDate(Instant recordDate) {
        this.recordDate = recordDate;
        return this;
    }

    public String getRegularity() {
        return regularity;
    }

    public Dividend setRegularity(String regularity) {
        this.regularity = regularity;
        return this;
    }

    public Money getClosePrice() {
        return closePrice;
    }

    public Dividend setClosePrice(Money closePrice) {
        this.closePrice = closePrice;
        return this;
    }

    public Quotation getYieldValue() {
        return yieldValue;
    }

    public Dividend setYieldValue(Quotation yieldValue) {
        this.yieldValue = yieldValue;
        return this;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Dividend setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }
}
