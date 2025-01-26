package com.siberalt.singularity.broker.impl.mock.shared.operation;

public class OpenPosition {
    protected long initialBalance = 0;
    protected long initialBlocked = 0;
    protected String instrumentId;
    protected String instrumentType;
    protected String positionId;
    protected boolean exchangeBlocked = false;

    public long getInitialBalance() {
        return initialBalance;
    }

    public OpenPosition setInitialBalance(long initialBalance) {
        this.initialBalance = initialBalance;
        return this;
    }

    public long getInitialBlocked() {
        return initialBlocked;
    }

    public OpenPosition setInitialBlocked(long initialBlocked) {
        this.initialBlocked = initialBlocked;
        return this;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public OpenPosition setInstrumentId(String instrumentId) {
        this.instrumentId = instrumentId;
        return this;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public OpenPosition setInstrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
        return this;
    }

    public String getPositionId() {
        return positionId;
    }

    public OpenPosition setPositionId(String positionId) {
        this.positionId = positionId;
        return this;
    }

    public boolean isExchangeBlocked() {
        return exchangeBlocked;
    }

    public OpenPosition setExchangeBlocked(boolean exchangeBlocked) {
        this.exchangeBlocked = exchangeBlocked;
        return this;
    }
}
