package investtech.broker.contract.service.operation.response;

public class PositionSecurities {
    protected long blocked;
    protected long balance;
    protected String positionUid;
    protected String instrumentUid;
    protected boolean exchangeBlocked;
    protected String instrumentType;

    public long getBlocked() {
        return blocked;
    }

    public PositionSecurities setBlocked(long blocked) {
        this.blocked = blocked;
        return this;
    }

    public long getBalance() {
        return balance;
    }

    public PositionSecurities setBalance(long balance) {
        this.balance = balance;
        return this;
    }

    public String getPositionUid() {
        return positionUid;
    }

    public PositionSecurities setPositionUid(String positionUid) {
        this.positionUid = positionUid;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public PositionSecurities setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public boolean isExchangeBlocked() {
        return exchangeBlocked;
    }

    public PositionSecurities setExchangeBlocked(boolean exchangeBlocked) {
        this.exchangeBlocked = exchangeBlocked;
        return this;
    }

    public String getInstrumentType() {
        return instrumentType;
    }

    public PositionSecurities setInstrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
        return this;
    }
}
