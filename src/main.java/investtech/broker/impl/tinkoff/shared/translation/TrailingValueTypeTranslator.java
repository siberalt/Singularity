package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.service.order.stop.request.TrailingValueType;

public class TrailingValueTypeTranslator {
    public static ru.tinkoff.piapi.contract.v1.TrailingValueType toTinkoff(TrailingValueType trailingValueType) {
        return switch (trailingValueType) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.TrailingValueType.TRAILING_VALUE_UNSPECIFIED;
            case ABSOLUTE -> ru.tinkoff.piapi.contract.v1.TrailingValueType.TRAILING_VALUE_ABSOLUTE;
            case RELATIVE -> ru.tinkoff.piapi.contract.v1.TrailingValueType.TRAILING_VALUE_RELATIVE;
        };
    }

    public static TrailingValueType toContract(ru.tinkoff.piapi.contract.v1.TrailingValueType takeProfitType) {
        return switch (takeProfitType) {
            case TRAILING_VALUE_UNSPECIFIED -> TrailingValueType.UNSPECIFIED;
            case TRAILING_VALUE_ABSOLUTE -> TrailingValueType.ABSOLUTE;
            case TRAILING_VALUE_RELATIVE -> TrailingValueType.RELATIVE;
            case UNRECOGNIZED -> null;
        };
    }
}
