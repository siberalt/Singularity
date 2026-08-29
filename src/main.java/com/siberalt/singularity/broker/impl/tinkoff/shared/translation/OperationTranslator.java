package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.entity.operation.Operation;

public class OperationTranslator {
    /**
     * Tinkoff's OperationItem (from GetOperationsByCursor) carries a single {@code date}
     * timestamp (no separate "created" vs "executed" moments the way our mock-broker-produced
     * operations do), so both {@code date} and {@code executedDate} are populated from it here.
     */
    public static Operation toContract(ru.tinkoff.piapi.contract.v1.OperationItem item, String accountId) {
        var date = TimestampTranslator.toContract(item.getDate());

        return Operation.builder()
            .id(item.getId())
            .accountId(accountId)
            .instrumentUid(item.getInstrumentUid())
            .direction(OperationTypeTranslator.toContract(item.getType()))
            .quantity(item.getQuantity())
            .quantityDone(item.getQuantityDone())
            .price(MoneyValueTranslator.toContract(item.getPrice()).getQuotation())
            .payment(MoneyValueTranslator.toContract(item.getPayment()).getQuotation())
            .state(OperationStateTranslator.toContract(item.getState()))
            .date(date)
            .executedDate(date)
            .build();
    }
}
