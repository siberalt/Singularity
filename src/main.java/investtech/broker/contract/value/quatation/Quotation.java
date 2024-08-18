package investtech.broker.contract.value.quatation;

import java.math.BigDecimal;

public class Quotation {
    protected long units;
    protected int nano;

    protected BigDecimal value;

    public long getUnits() {
        return units;
    }

    public Quotation setUnits(long units) {
        this.units = units;
        return this;
    }

    public int getNano() {
        return nano;
    }

    public Quotation setNano(int nano) {
        this.nano = nano;
        return this;
    }

    public Quotation add(Quotation quotation) {
        return Quotation.of(toBigDecimal().add(quotation.toBigDecimal()));
    }

    @Override
    public String toString() {
        return toBigDecimal().toString();
    }

    public BigDecimal toBigDecimal() {
        if (null == value) {
            value = units == 0 && nano == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(units).add(BigDecimal.valueOf(nano, 9));
        }

        return value;
    }

    public static Quotation of(String value) {
        return Quotation.of(BigDecimal.valueOf(Double.parseDouble(value)));
    }

    public static Quotation of(Double value) {
        return Quotation.of(BigDecimal.valueOf(value));
    }

    public static Quotation of(BigDecimal value) {
        return new Quotation()
                .setUnits(value.longValue())
                .setNano(value.remainder(BigDecimal.ONE).multiply(BigDecimal.valueOf(1_000_000_000)).intValue());
    }
}
