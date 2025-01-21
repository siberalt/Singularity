package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.common.ListTranslator;
import investtech.broker.contract.service.order.response.OrderState;

public class OrderStateTranslator {
    public static ru.tinkoff.piapi.contract.v1.OrderState toTinkoff(OrderState orderState) {
        return ru.tinkoff.piapi.contract.v1.OrderState.newBuilder()
                .setOrderId(orderState.getOrderId())
                .setExecutionReportStatus(OrderExecutionReportStatusTranslator.toTinkoff(orderState.getExecutionStatus()))
                .setLotsRequested(orderState.getLotsRequested())
                .setLotsExecuted(orderState.getLotsExecuted())
                .setInitialOrderPrice(MoneyValueTranslator.toTinkoff(orderState.getInitialOrderPrice()))
                .setExecutedOrderPrice(MoneyValueTranslator.toTinkoff(orderState.getExecutedOrderPrice()))
                .setTotalOrderAmount(MoneyValueTranslator.toTinkoff(orderState.getTotalPrice()))
                .setAveragePositionPrice(MoneyValueTranslator.toTinkoff(orderState.getAveragePositionPrice()))
                .setInitialCommission(MoneyValueTranslator.toTinkoff(orderState.getInitialCommission()))
                .setExecutedCommission(MoneyValueTranslator.toTinkoff(orderState.getExecutedCommission()))
                .setDirection(OrderDirectionTranslator.toTinkoff(orderState.getDirection()))
                .setInitialSecurityPrice(MoneyValueTranslator.toTinkoff(orderState.getInitialSecurityPrice()))
                .addAllStages(ListTranslator.translate(orderState.getStages(), OrderStageTranslator::toTinkoff))
                .setServiceCommission(MoneyValueTranslator.toTinkoff(orderState.getServiceCommission()))
                .setCurrency(orderState.getCurrency())
                .setOrderType(OrderTypeTranslator.toTinkoff(orderState.getOrderType()))
                .setOrderDate(TimestampTranslator.toTinkoff(orderState.getOrderDate()))
                .setInstrumentUid(orderState.getInstrumentUid())
                .setOrderRequestId(orderState.getIdempotencyKey())
                .build();
    }

    public static OrderState toContract(ru.tinkoff.piapi.contract.v1.OrderState orderState) {
        return new OrderState()
                .setOrderId(orderState.getOrderId())
                .setExecutionStatus(OrderExecutionReportStatusTranslator.toContract(orderState.getExecutionReportStatus()))
                .setLotsRequested(orderState.getLotsRequested())
                .setLotsExecuted(orderState.getLotsExecuted())
                .setInitialOrderPrice(MoneyValueTranslator.toContract(orderState.getInitialOrderPrice()))
                .setExecutedOrderPrice(MoneyValueTranslator.toContract(orderState.getExecutedOrderPrice()))
                .setTotalPrice(MoneyValueTranslator.toContract(orderState.getTotalOrderAmount()))
                .setAveragePositionPrice(MoneyValueTranslator.toContract(orderState.getAveragePositionPrice()))
                .setInitialCommission(MoneyValueTranslator.toContract(orderState.getInitialCommission()))
                .setExecutedCommission(MoneyValueTranslator.toContract(orderState.getExecutedCommission()))
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
}
