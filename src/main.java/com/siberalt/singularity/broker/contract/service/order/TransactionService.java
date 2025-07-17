package com.siberalt.singularity.broker.contract.service.order;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.transaction.Transaction;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.strategy.context.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TransactionService {
    private final List<TransactionSpecProvider> aggregators = new ArrayList<>();

    public TransactionService addProvider(TransactionSpecProvider aggregator) {
        this.aggregators.add(aggregator);
        return this;
    }

    public List<TransactionSpec> calculateSpecs(Order order) {
        List<TransactionSpec> transactionSpecs = new ArrayList<>();
        for (TransactionSpecProvider aggregator : aggregators) {
            aggregator.provide(order).ifPresent(transactionSpecs::add);
        }
        return transactionSpecs;
    }

    public Quotation sumSpecs(List<TransactionSpec> transactionSpecs) {
        return transactionSpecs.stream()
            .map(spec -> spec.amount().getQuotation())
            .reduce(Quotation.ZERO, Quotation::add);
    }

    public List<Transaction> create(Order order, String brokerId, Clock clock) {
        return create(calculateSpecs(order), order.getAccountId(), brokerId, clock);
    }

    public List<Transaction> create(
        List<TransactionSpec> transactionSpecs,
        String accountId,
        String brokerId,
        Clock clock
    ) {
        List<Transaction> transactions = new ArrayList<>();

        for (TransactionSpec spec : transactionSpecs) {
            Transaction transaction = new Transaction()
                .setId(UUID.randomUUID().toString())
                .setDescription(spec.description())
                .setType(spec.type())
                .setAmount(spec.amount())
                .setDestinationAccountId(accountId)
                .setSourceAccountId(brokerId)
                .setCreatedTime(clock.currentTime());
            transactions.add(transaction);
        }

        return transactions;
    }
}
