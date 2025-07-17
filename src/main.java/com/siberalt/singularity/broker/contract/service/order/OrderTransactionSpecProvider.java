package com.siberalt.singularity.broker.contract.service.order;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.entity.transaction.TransactionType;

import java.util.Optional;
import java.util.logging.Logger;

public class OrderTransactionSpecProvider implements TransactionSpecProvider {
    private static final Logger logger = Logger.getLogger(OrderTransactionSpecProvider.class.getName());

    @Override
    public Optional<TransactionSpec> provide(Order order) {
        validateOrder(order);

        Instrument instrument = order.getInstrument();
        Quotation amount = calculateAmount(order, instrument);

        if (amount == null) {
            return Optional.empty();
        }

        TransactionType transactionType = order.getDirection().isBuy() ? TransactionType.BUY : TransactionType.SELL;
        String description = transactionType == TransactionType.BUY ? "Buy order transaction" : "Sell order transaction";
        String instrumentCurrency = instrument.getCurrency();

        TransactionSpec transaction = new TransactionSpec(
            transactionType,
            description,
            Money.of(instrumentCurrency, amount)
        );
        logger.info("Created transaction: " + transaction);

        return Optional.of(transaction);
    }

    private void validateOrder(Order order) {
        if (order.getInstrument() == null || order.getInstrumentPrice() == null || order.getLotsRequested() <= 0) {
            throw new IllegalArgumentException("Invalid order properties.");
        }
    }

    private Quotation calculateAmount(Order order, Instrument instrument) {
        Quotation price = order.getInstrumentPrice();
        long lots = order.getLotsRequested();
        long lotSize = instrument.getLot();

        if (order.getDirection().isBuy()) {
            return price.multiply(lots).multiply(lotSize).multiply(-1);
        } else if (order.getDirection().isSell()) {
            return price.multiply(lots).multiply(lotSize);
        }

        return null;
    }
}
