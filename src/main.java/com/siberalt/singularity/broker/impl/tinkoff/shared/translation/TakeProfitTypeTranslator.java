package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.order.stop.request.TakeProfitType;

public class TakeProfitTypeTranslator {
    public static ru.tinkoff.piapi.contract.v1.TakeProfitType toTinkoff(TakeProfitType takeProfitType) {
        return switch (takeProfitType) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.TakeProfitType.TAKE_PROFIT_TYPE_UNSPECIFIED;
            case REGULAR -> ru.tinkoff.piapi.contract.v1.TakeProfitType.TAKE_PROFIT_TYPE_REGULAR;
            case TRAILING -> ru.tinkoff.piapi.contract.v1.TakeProfitType.TAKE_PROFIT_TYPE_TRAILING;
        };
    }

    public static TakeProfitType toContract(ru.tinkoff.piapi.contract.v1.TakeProfitType takeProfitType) {
        return switch (takeProfitType) {
            case TAKE_PROFIT_TYPE_UNSPECIFIED -> TakeProfitType.UNSPECIFIED;
            case TAKE_PROFIT_TYPE_REGULAR -> TakeProfitType.REGULAR;
            case TAKE_PROFIT_TYPE_TRAILING -> TakeProfitType.TRAILING;
            case UNRECOGNIZED -> null;
        };
    }
}
