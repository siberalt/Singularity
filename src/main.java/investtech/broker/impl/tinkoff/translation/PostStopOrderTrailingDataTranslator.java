package investtech.broker.impl.tinkoff.translation;


import investtech.broker.contract.service.order.stop.request.PostStopOrderRequest;

public class PostStopOrderTrailingDataTranslator {
    public static ru.tinkoff.piapi.contract.v1.PostStopOrderRequest.TrailingData toTinkoff(
            PostStopOrderRequest.TrailingData trailingData
    ) {
        return ru.tinkoff.piapi.contract.v1.PostStopOrderRequest.TrailingData.newBuilder()
                .setIndent(QuotationTranslator.toTinkoff(trailingData.getIndent()))
                .setIndentType(TrailingValueTypeTranslator.toTinkoff(trailingData.getIndentType()))
                .setSpread(QuotationTranslator.toTinkoff(trailingData.getSpread()))
                .setSpreadType(TrailingValueTypeTranslator.toTinkoff(trailingData.getSpreadType()))
                .build();
    }

    public static PostStopOrderRequest.TrailingData toContract(
            ru.tinkoff.piapi.contract.v1.PostStopOrderRequest.TrailingData trailingData
    ) {
        return new PostStopOrderRequest.TrailingData()
                .setIndent(QuotationTranslator.toContract(trailingData.getIndent()))
                .setIndentType(TrailingValueTypeTranslator.toContract(trailingData.getIndentType()))
                .setSpread(QuotationTranslator.toContract(trailingData.getSpread()))
                .setSpreadType(TrailingValueTypeTranslator.toContract(trailingData.getSpreadType()));
    }
}
