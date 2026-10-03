package com.siberalt.singularity.broker.contract.service.order;

import com.siberalt.singularity.entity.transaction.TransactionType;
import com.siberalt.singularity.broker.contract.service.order.commission.CommissionPolicy;
import com.siberalt.singularity.broker.contract.service.order.commission.FlatRateCommission;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.broker.contract.value.money.Money;

import java.util.Optional;

/**
 * Turns what a {@link CommissionPolicy} charges into a ledger entry.
 * <p>
 * The arithmetic used to live here as one flat ratio of the notional. It moved to
 * {@link CommissionPolicy} so that a tariff can differ by side, have a floor, or depend on the instrument,
 * and this class is now only the bridge between a policy and the operations ledger. The ratio-taking
 * constructor stays and wraps {@link FlatRateCommission}: every backtest in {@code docs/signals.md} was
 * measured through it, and those numbers have to stay reproducible.
 */
public class CommissionTransactionSpecProvider implements TransactionSpecProvider {
    private CommissionPolicy policy;

    public CommissionTransactionSpecProvider(CommissionPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Нужна политика комиссии");
        }

        this.policy = policy;
    }

    public CommissionTransactionSpecProvider(double commissionRate) {
        this(new FlatRateCommission(commissionRate));
    }

    @Override
    public Optional<TransactionSpec> provide(Order order) {
        Money amount = policy.of(order, order.getLotsRequested());

        if (amount == null) {
            return Optional.empty();
        }

        return Optional.of(new TransactionSpec(
            TransactionType.COMMISSION,
            "Standard commission for market orders",
            amount
        ));
    }

    /** Replaces the tariff wholesale - what a study does when it wants a different one. */
    public void setCommissionPolicy(CommissionPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("Нужна политика комиссии");
        }

        this.policy = policy;
    }

    public void setCommissionRatio(double commissionRatio) {
        this.policy = new FlatRateCommission(commissionRatio);
    }
}
