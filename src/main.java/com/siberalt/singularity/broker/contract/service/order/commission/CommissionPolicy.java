package com.siberalt.singularity.broker.contract.service.order.commission;

import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.broker.contract.value.money.Money;

/**
 * What a broker charges for one fill.
 * <p>
 * This used to be a single {@code double} threaded from a builder through the mock broker's service
 * context into one concrete provider, which meant every simulation paid the same flat share of its
 * notional in both directions and nothing else was expressible. Real tariffs are not that shape: they
 * differ by side, they have a minimum per order, they are per-lot on some instruments and tiered by turnover
 * on others, and a short pays things a long does not. So the calculation belongs here, in the contract,
 * where a broker implementation or a study can say what its own tariff is.
 * <p>
 * The returned amount is a <b>cost</b> and is therefore negative, in the instrument's currency: the ledger
 * adds transactions rather than subtracting them, and a policy that returns a positive number is a rebate.
 * Implementations are asked once per fill with the lots actually filled, not the lots requested - a partial
 * fill is charged for what it filled.
 *
 * @see FlatRateCommission the behaviour everything here was measured against: one ratio, both sides
 * @see SideCommission per-side ratios with a minimum per order, which is what a retail tariff looks like
 */
public interface CommissionPolicy {
    /** Nothing charged - for simulations measuring gross behaviour. */
    CommissionPolicy FREE = (order, lots) -> Money.of(order.getInstrument().getCurrency(), 0.0);

    /**
     * @param lots how many lots this fill filled, which may be fewer than the order asked for
     * @return the charge, negative for a cost, in the instrument's currency
     */
    Money of(Order order, long lots);
}
