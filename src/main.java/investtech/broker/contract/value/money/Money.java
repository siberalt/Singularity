package investtech.broker.contract.value.money;

import java.math.BigDecimal;

public class Money {
    protected String currencyIso;

    protected long units;

    protected int nano;

    public String getCurrencyIso() {
        return currencyIso;
    }

    public Money setCurrencyIso(String currencyIso) {
        this.currencyIso = currencyIso;
        return this;
    }

    public long getUnits() {
        return units;
    }

    public Money setUnits(long units) {
        this.units = units;
        return this;
    }

    public int getNano() {
        return nano;
    }

    public Money setNano(int nano) {
        this.nano = nano;
        return this;
    }

    @Override
    public String toString() {
        return String.format("%s:%s", getCurrencyIso(), toBigDecimal().toString());
    }

    public BigDecimal toBigDecimal() {
        return units == 0 && nano == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
    }
}
