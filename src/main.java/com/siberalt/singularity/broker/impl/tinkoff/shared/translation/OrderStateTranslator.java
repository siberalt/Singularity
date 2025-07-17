package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.order.response.OrderState;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.entity.transaction.TransactionType;

import java.util.List;

public class OrderStateTranslator {
    public static OrderState toContract(ru.tinkoff.piapi.contract.v1.OrderState orderState) {
        return new OrderState()
            .setOrderId(orderState.getOrderId())
            .setExecutionStatus(OrderExecutionReportStatusTranslator.toContract(orderState.getExecutionReportStatus()))
            .setLotsRequested(orderState.getLotsRequested())
            .setLotsExecuted(orderState.getLotsExecuted())
            .setInitialOrderPrice(MoneyValueTranslator.toContract(orderState.getInitialOrderPrice()))
            .setExecutedOrderPrice(MoneyValueTranslator.toContract(orderState.getExecutedOrderPrice()))
            .setBalanceChange(MoneyValueTranslator.toContract(orderState.getTotalOrderAmount()))
            .setAveragePositionPrice(MoneyValueTranslator.toContract(orderState.getAveragePositionPrice()))
            .setTransactions(List.of(
                calculateCommission(
                    orderState.getExecutedCommission(),
                    orderState.getInitialCommission()
                )
            ))
            .setDirection(OrderDirectionTranslator.toContract(orderState.getDirection()))
            .setInitialSecurityPrice(MoneyValueTranslator.toContract(orderState.getInitialSecurityPrice()))
            .setStages(ListTranslator.translate(orderState.getStagesList(), OrderStageTranslator::toContract))
            .setServiceCommission(MoneyValueTranslator.toContract(orderState.getServiceCommission()))
            .setCurrency(orderState.getCurrency())
            .setOrderType(OrderTypeTranslator.toContract(orderState.getOrderType()))
            .setOrderDate(TimestampTranslator.toContract(orderState.getOrderDate()))
            .setInstrumentUid(orderState.getInstrumentUid())
            .setIdempotencyKey(orderState.getOrderRequestId());
    }

    public static TransactionSpec calculateCommission(
        ru.tinkoff.piapi.contract.v1.MoneyValue executedCommission,
        ru.tinkoff.piapi.contract.v1.MoneyValue initialCommission
    ) {
        Money commissionMoney = MoneyValueTranslator.toContract(
            ru.tinkoff.piapi.contract.v1.MoneyValue.getDefaultInstance().equals(executedCommission)
                ? initialCommission
                : executedCommission
        );

        return new TransactionSpec(TransactionType.COMMISSION, "Commission", commissionMoney);
    }
}
