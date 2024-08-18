package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.order.request.PriceType;

public class PriceTypeTranslator {
    public static ru.tinkoff.piapi.contract.v1.PriceType toTinkoff(PriceType priceType) {
        return switch (priceType) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.PriceType.PRICE_TYPE_UNSPECIFIED;
            case POINT -> ru.tinkoff.piapi.contract.v1.PriceType.PRICE_TYPE_POINT;
            case CURRENCY -> ru.tinkoff.piapi.contract.v1.PriceType.PRICE_TYPE_CURRENCY;
        };
    }

    public static PriceType toContract(ru.tinkoff.piapi.contract.v1.PriceType priceType) {
        return switch (priceType) {
            case PRICE_TYPE_UNSPECIFIED -> PriceType.UNSPECIFIED;
            case PRICE_TYPE_POINT -> PriceType.POINT;
            case PRICE_TYPE_CURRENCY -> PriceType.CURRENCY;
            case UNRECOGNIZED -> null;
        };
    }
}
