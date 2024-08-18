package investtech.broker.impl.tinkoff.translation;


import investtech.broker.contract.service.order.stop.response.StopOrder;

public class StopOrderTrailingDataTranslator {
    public static ru.tinkoff.piapi.contract.v1.StopOrder.TrailingData toTinkoff(
            StopOrder.TrailingData trailingData
    ) {
        return ru.tinkoff.piapi.contract.v1.StopOrder.TrailingData.newBuilder()
                .setIndent(QuotationTranslator.toTinkoff(trailingData.getIndent()))
                .setIndentType(TrailingValueTypeTranslator.toTinkoff(trailingData.getIndentType()))
                .setSpread(QuotationTranslator.toTinkoff(trailingData.getSpread()))
                .setSpreadType(TrailingValueTypeTranslator.toTinkoff(trailingData.getSpreadType()))
                .setExtr(QuotationTranslator.toTinkoff(trailingData.getExtr()))
                .setStatus(TrailingStopStatusTranslator.toTinkoff(trailingData.getStatus()))
                .setPrice(QuotationTranslator.toTinkoff(trailingData.getPrice()))
                .build();
    }

    public static StopOrder.TrailingData toContract(
            ru.tinkoff.piapi.contract.v1.StopOrder.TrailingData trailingData
    ) {
        return new StopOrder.TrailingData()
                .setIndent(QuotationTranslator.toContract(trailingData.getIndent()))
                .setIndentType(TrailingValueTypeTranslator.toContract(trailingData.getIndentType()))
                .setSpread(QuotationTranslator.toContract(trailingData.getSpread()))
                .setSpreadType(TrailingValueTypeTranslator.toContract(trailingData.getSpreadType()))
                .setExtr(QuotationTranslator.toContract(trailingData.getExtr()))
                .setStatus(TrailingStopStatusTranslator.toContract(trailingData.getStatus()))
                .setPrice(QuotationTranslator.toContract(trailingData.getPrice()));
    }
}
