package com.siberalt.singularity.broker.contract.service.order.commission;

import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

/**
 * A rate per side with a floor per order - the shape of a retail tariff.
 * <p>
 * Two things here that {@link FlatRateCommission} cannot say. The sides can differ, which matters as soon
 * as shorting exists: a sale that opens a short and a sale that closes a long are charged the same by the
 * exchange but not always by the broker. And there is a minimum per order, which is what makes small orders
 * expensive and is the reason a rule firing often on a small account can lose to its own costs while the
 * same rule on a large one pays.
 * <p>
 * The floor is per <b>fill</b>, not per order, because that is what this contract is asked about. A broker
 * that charges its minimum once per order regardless of how many fills it took would need the order's
 * accumulated charges, which the ledger has and this interface deliberately does not - a policy is a
 * function of one fill, so anything stateful belongs in the service that calls it.
 */
public class SideCommission implements CommissionPolicy {
    private final double buyRate;
    private final double sellRate;
    private final double least;

    /**
     * @param buyRate  share of the notional on a purchase
     * @param sellRate share of the notional on a sale
     * @param least    the smallest charge per fill, in the instrument's currency; zero for no floor
     */
    public SideCommission(double buyRate, double sellRate, double least) {
        if (buyRate < 0 || buyRate > 1 || sellRate < 0 || sellRate > 1) {
            throw new IllegalArgumentException("Доли комиссии должны быть между 0 и 1, получено "
                + buyRate + " и " + sellRate);
        }

        if (least < 0) {
            throw new IllegalArgumentException("Минимальная комиссия не может быть отрицательной: " + least);
        }

        this.buyRate = buyRate;
        this.sellRate = sellRate;
        this.least = least;
    }

    public SideCommission(double rate, double least) {
        this(rate, rate, least);
    }

    @Override
    public Money of(Order order, long lots) {
        Quotation notional = order.getInstrumentPrice()
            .multiply(lots)
            .multiply(order.getInstrument().getLot());
        double rate = order.getDirection() != null && order.getDirection().isBuy() ? buyRate : sellRate;
        // Через арифметику Quotation, а не через double: превращение дроби в units и nano туда и обратно
        // теряет последние разряды, и ровные шесть рублей становились 5.999999999.
        Quotation share = notional.multiply(rate);
        Quotation charged = share.toDouble() >= least ? share : Quotation.of(least);

        return Money.of(order.getInstrument().getCurrency(), charged.multiply(-1));
    }
}
