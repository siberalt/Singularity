package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.service.market.response.TechAnalysisItem;
import ru.tinkoff.piapi.contract.v1.GetTechAnalysisResponse;

public class TechAnalysisItemTranslator {
    public static GetTechAnalysisResponse.TechAnalysisItem toTinkoff(TechAnalysisItem techAnalysisItem) {
        return ru.tinkoff.piapi.contract.v1.GetTechAnalysisResponse.TechAnalysisItem.newBuilder()
                .setTimestamp(TimestampTranslator.toTinkoff(techAnalysisItem.getTimestamp()))
                .setMiddleBand(QuotationTranslator.toTinkoff(techAnalysisItem.getMiddleBand()))
                .setUpperBand(QuotationTranslator.toTinkoff(techAnalysisItem.getUpperBand()))
                .setLowerBand(QuotationTranslator.toTinkoff(techAnalysisItem.getLowerBand()))
                .setSignal(QuotationTranslator.toTinkoff(techAnalysisItem.getSignal()))
                .setMacd(QuotationTranslator.toTinkoff(techAnalysisItem.getMacd()))
                .build();
    }

    public static TechAnalysisItem toContract(GetTechAnalysisResponse.TechAnalysisItem techAnalysisItem) {
        return new TechAnalysisItem()
                .setTimestamp(TimestampTranslator.toContract(techAnalysisItem.getTimestamp()))
                .setMiddleBand(QuotationTranslator.toContract(techAnalysisItem.getMiddleBand()))
                .setUpperBand(QuotationTranslator.toContract(techAnalysisItem.getUpperBand()))
                .setLowerBand(QuotationTranslator.toContract(techAnalysisItem.getLowerBand()))
                .setSignal(QuotationTranslator.toContract(techAnalysisItem.getSignal()))
                .setMacd(QuotationTranslator.toContract(techAnalysisItem.getMacd()));
    }
}
