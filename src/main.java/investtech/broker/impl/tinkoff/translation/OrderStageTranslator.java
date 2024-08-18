package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.order.response.OrderStage;

public class OrderStageTranslator {
    public static ru.tinkoff.piapi.contract.v1.OrderStage toTinkoff(OrderStage stage) {
        return ru.tinkoff.piapi.contract.v1.OrderStage.newBuilder()
                .setPrice(MoneyValueTranslator.toTinkoff(stage.getPrice()))
                .setQuantity(stage.getQuantity())
                .setTradeId(stage.getTradeId())
                .setExecutionTime(TimestampTranslator.toTinkoff(stage.getExecutionTime()))
                .build();
    }

    public static OrderStage toContract(ru.tinkoff.piapi.contract.v1.OrderStage stage) {
        return new OrderStage()
                .setExecutionTime(TimestampTranslator.toContract(stage.getExecutionTime()))
                .setQuantity(stage.getQuantity())
                .setTradeId(stage.getTradeId())
                .setPrice(MoneyValueTranslator.toContract(stage.getPrice()));
    }
}
