package com.siberalt.singularity.broker.contract.service.operation.response;

public class Position {
    protected long blocked;
    protected long balance;
    protected String positionUid;
    protected String instrumentUid;
    protected boolean exchangeBlocked;
    protected String instrumentType;

    public long getBlocked() {
        return blocked;
    }

    public Position setBlocked(long blocked) {
        this.blocked = blocked;
        return this;
    }

    public long getBalance() {
        return balance;
    }

    public Position setBalance(long balance) {
        this.balance = balance;
        return this;
    }

    public String getPositionUid() {
        return positionUid;
    }

    public Position setPositionUid(String positionUid) {
        this.positionUid = positionUid;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public Position setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public boolean isExchangeBlocked() {
        return exchangeBlocked;
    }

    public Position setExchangeBlocked(boolean exchangeBlocked) {
        this.exchangeBlocked = exchangeBlocked;
        return this;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public Position setInstrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
        return this;
    }
}
