package investtech.broker.impl.tinkoff.shared.translation;


import investtech.broker.contract.service.market.request.PriceType;

public class TypeOfPriceTranslator {
    public static ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice toTinkoff(PriceType priceType) {
        return switch (priceType) {
            case UNSPECIFIED ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice.TYPE_OF_PRICE_UNSPECIFIED;
            case CLOSE -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice.TYPE_OF_PRICE_CLOSE;
            case OPEN -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice.TYPE_OF_PRICE_OPEN;
            case HIGH -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice.TYPE_OF_PRICE_HIGH;
            case LOW -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice.TYPE_OF_PRICE_LOW;
            case AVG -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice.TYPE_OF_PRICE_AVG;
        };
    }

    public static PriceType toContract( ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice typeOfPrice) {
        return switch (typeOfPrice) {
            case TYPE_OF_PRICE_UNSPECIFIED -> PriceType.UNSPECIFIED;
            case TYPE_OF_PRICE_CLOSE -> PriceType.CLOSE;
            case TYPE_OF_PRICE_OPEN -> PriceType.OPEN;
            case TYPE_OF_PRICE_HIGH -> PriceType.HIGH;
            case TYPE_OF_PRICE_LOW -> PriceType.LOW;
            case TYPE_OF_PRICE_AVG -> PriceType.AVG;
            case UNRECOGNIZED -> null;
        };
    }
}
