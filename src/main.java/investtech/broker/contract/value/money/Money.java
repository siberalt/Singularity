package investtech.broker.contract.value.money;

import investtech.broker.contract.value.quotation.Quotation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents a monetary value with a specific currency.
 */
public class Money {
    protected String currencyIso;
    protected Quotation quotation;

    public Money setQuotation(Quotation quotation) {
        Objects.requireNonNull(quotation);
        this.quotation = quotation;
        return this;
    }

    public Quotation getQuotation() {
        return quotation;
    }

    public String getCurrencyIso() {
        return currencyIso;
    }

    public Money setCurrencyIso(String currencyIso) {
        if (currencyIso == null || currencyIso.isEmpty()) {
            throw new IllegalArgumentException("Currency ISO cannot be null or empty");
        }
        this.currencyIso = currencyIso;
        return this;
    }

    public Money add(Money value) {
        checkSameCurrency(value);
        return Money.of(currencyIso, quotation.add(value.quotation));
    }

    public void addCumulative(Money value) {
        checkSameCurrency(value);
        quotation = quotation.add(value.quotation);
    }

    public Money subtract(Money value) {
        checkSameCurrency(value);
        return Money.of(currencyIso, quotation.subtract(value.quotation));
    }

    public void subtractCumulative(Money value) {
        checkSameCurrency(value);
        quotation = quotation.subtract(value.quotation);
    }

    public Money multiply(int multiplier) {
        return Money.of(currencyIso, quotation.multiply(multiplier));
    }

    public Money multiply(Quotation multiplier) {
        return Money.of(currencyIso, quotation.multiply(multiplier));
    }

    public Money multiply(BigDecimal multiplier) {
        return Money.of(currencyIso, quotation.multiply(multiplier));
    }

    public Money multiply(Money multiplier) {
        checkSameCurrency(multiplier);
        return multiply(multiplier.getQuotation());
    }

    public Money div(long divider) {
        return div(BigDecimal.valueOf(divider));
    }

    public Money div(int divider) {
        return div(BigDecimal.valueOf(divider));
    }

    public Money div(Quotation divider) {
        return Money.of(currencyIso, quotation.divide(divider));
    }

    public Money div(BigDecimal divider) {
        return Money.of(currencyIso, quotation.divide(divider));
    }

    public Money div(Money divider) {
        checkSameCurrency(divider);
        return div(divider.getQuotation());
    }
    // endregion Arithmetic operators

    // region logic operators
    public boolean isMoreThan(Money money) {
        checkSameCurrency(money);
        return quotation.isMore(money.getQuotation());
    }

    public boolean isMoreThanOrEqual(Money money) {
        checkSameCurrency(money);
        return quotation.isMoreOrEqual(money.getQuotation());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Money money = (Money) o;
        return Objects.equals(currencyIso, money.currencyIso) && Objects.equals(quotation, money.quotation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currencyIso, quotation);
    }

    // endregion logic operators
    protected void checkSameCurrency(Money value) {
        if (value == null) {
            throw new IllegalArgumentException("Money value cannot be null");
        }
        if (!Objects.equals(value.currencyIso, this.currencyIso)) {
            throw new IllegalArgumentException(
                String.format("Monies %s and %s are not the same currency", value.currencyIso, this.currencyIso)
            );
        }
    }

    public static Money of(String currencyIso, Quotation value) {
        return new Money()
            .setCurrencyIso(currencyIso)
            .setQuotation(value);
    }

    public static Money of(String currencyIso, String value) {
        return Money.of(currencyIso, BigDecimal.valueOf(Double.parseDouble(value)));
    }

    public static Money of(String currencyIso, Double value) {
        return Money.of(currencyIso, BigDecimal.valueOf(value));
    }

    public static Money of(String currencyIso, BigDecimal value) {
        return new Money()
            .setQuotation(Quotation.of(value))
            .setCurrencyIso(currencyIso);
    }

    @Override
    public String toString() {
        return String.format("%s:%s", getCurrencyIso(), quotation.toString());
    }
}
