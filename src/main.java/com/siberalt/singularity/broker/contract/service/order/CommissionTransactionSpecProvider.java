package com.siberalt.singularity.broker.contract.service.order;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.entity.transaction.TransactionType;

import java.util.Optional;

public class CommissionTransactionSpecProvider implements TransactionSpecProvider {
    private double commissionRate;

    public CommissionTransactionSpecProvider(double commissionRate) {
        this.commissionRate = commissionRate;
    }

    @Override
    public Optional<TransactionSpec> provide(Order order) {
        Quotation initialPrice = order.getInstrumentPrice()
            .multiply(order.getLotsRequested())
            .multiply(order.getInstrument().getLot());

        Money amount = Money.of(
            order.getInstrument().getCurrency(),
            initialPrice.multiply(commissionRate).multiply(-1)
        );

        return Optional.of(
            new TransactionSpec(
                TransactionType.COMMISSION,
                "Standard commission for market orders",
                amount
            )
        );
    }

    public void setCommissionRatio(double commissionRatio) {
        if (commissionRatio < 0 || commissionRatio > 1) {
            throw new IllegalArgumentException("Commission ratio must be between 0 and 1");
        }
        this.commissionRate = commissionRatio;
    }
}
