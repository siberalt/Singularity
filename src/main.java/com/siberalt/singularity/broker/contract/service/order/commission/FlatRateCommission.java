package com.siberalt.singularity.broker.contract.service.order.commission;

import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

/**
 * One share of the notional, the same in both directions - the tariff every backtest here was written
 * against, kept so that the numbers in {@code docs/signals.md} stay reproducible.
 * <p>
 * It is the simplest thing that is not free, and it is wrong in one way worth remembering: a real tariff
 * has a floor per order, so a tiny order pays far more than this says. On the sizes these simulations trade
 * that floor never binds, which is why this was good enough for five years of measurements - see
 * {@link SideCommission} for the shape that does model it.
 */
public class FlatRateCommission implements CommissionPolicy {
    private final double rate;

    public FlatRateCommission(double rate) {
        if (rate < 0 || rate > 1) {
            throw new IllegalArgumentException("Доля комиссии должна быть между 0 и 1, получено " + rate);
        }

        this.rate = rate;
    }

    @Override
    public Money of(Order order, long lots) {
        Quotation notional = order.getInstrumentPrice()
            .multiply(lots)
            .multiply(order.getInstrument().getLot());

        return Money.of(order.getInstrument().getCurrency(), notional.multiply(rate).multiply(-1));
    }

    public double rate() {
        return rate;
    }
}
