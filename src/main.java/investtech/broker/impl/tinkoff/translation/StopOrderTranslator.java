package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.order.stop.response.StopOrder;

public class StopOrderTranslator {
    public static ru.tinkoff.piapi.contract.v1.StopOrder toTinkoff(StopOrder stopOrder) {
        return ru.tinkoff.piapi.contract.v1.StopOrder.newBuilder()
                .setStopOrderId(stopOrder.getStopOrderId())
                .setLotsRequested(stopOrder.getLotsRequested())
                .setDirection(StopOrderDirectionTranslator.toTinkoff(stopOrder.getDirection()))
                .setCurrency(stopOrder.getCurrency())
                .setOrderType(StopOrderTypeTranslator.toTinkoff(stopOrder.getOrderType()))
                .setCreateDate(TimestampTranslator.toTinkoff(stopOrder.getCreateDate()))
                .setActivationDateTime(TimestampTranslator.toTinkoff(stopOrder.getActivationDateTime()))
                .setExpirationTime(TimestampTranslator.toTinkoff(stopOrder.getExpirationTime()))
                .setPrice(MoneyValueTranslator.toTinkoff(stopOrder.getPrice()))
                .setStopPrice(MoneyValueTranslator.toTinkoff(stopOrder.getStopPrice()))
                .setInstrumentUid(stopOrder.getInstrumentUid())
                .setTakeProfitType(TakeProfitTypeTranslator.toTinkoff(stopOrder.getTakeProfitType()))
                .setTrailingData(StopOrderTrailingDataTranslator.toTinkoff(stopOrder.getTrailingData()))
                .setStatus(StopOrderStatusOptionTranslator.toTinkoff(stopOrder.getStatus()))
                .setExchangeOrderType(ExchangeOrderTypeTranslator.toTinkoff(stopOrder.getExchangeOrderType()))
                .build();
    }

    public static StopOrder toContract(ru.tinkoff.piapi.contract.v1.StopOrder stopOrder) {
        return new StopOrder()
                .setStopOrderId(stopOrder.getStopOrderId())
                .setLotsRequested(stopOrder.getLotsRequested())
                .setDirection(StopOrderDirectionTranslator.toContract(stopOrder.getDirection()))
                .setCurrency(stopOrder.getCurrency())
                .setOrderType(StopOrderTypeTranslator.toContract(stopOrder.getOrderType()))
                .setCreateDate(TimestampTranslator.toContract(stopOrder.getCreateDate()))
                .setActivationDateTime(TimestampTranslator.toContract(stopOrder.getActivationDateTime()))
                .setExpirationTime(TimestampTranslator.toContract(stopOrder.getExpirationTime()))
                .setPrice(MoneyValueTranslator.toContract(stopOrder.getPrice()))
                .setStopPrice(MoneyValueTranslator.toContract(stopOrder.getStopPrice()))
                .setInstrumentUid(stopOrder.getInstrumentUid())
                .setTakeProfitType(TakeProfitTypeTranslator.toContract(stopOrder.getTakeProfitType()))
                .setTrailingData(StopOrderTrailingDataTranslator.toContract(stopOrder.getTrailingData()))
                .setStatus(StopOrderStatusOptionTranslator.toContract(stopOrder.getStatus()))
                .setExchangeOrderType(ExchangeOrderTypeTranslator.toContract(stopOrder.getExchangeOrderType()));
    }
}
