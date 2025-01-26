package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;


import com.siberalt.singularity.broker.contract.service.order.stop.response.TrailingStopStatus;

public class TrailingStopStatusTranslator {
    public static ru.tinkoff.piapi.contract.v1.TrailingStopStatus toTinkoff(
            TrailingStopStatus trailingStopStatus
    ) {
        return switch (trailingStopStatus) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.TrailingStopStatus.TRAILING_STOP_UNSPECIFIED;
            case ACTIVE -> ru.tinkoff.piapi.contract.v1.TrailingStopStatus.TRAILING_STOP_ACTIVE;
            case ACTIVATED -> ru.tinkoff.piapi.contract.v1.TrailingStopStatus.TRAILING_STOP_ACTIVATED;
        };
    }

    public static TrailingStopStatus toContract(
            ru.tinkoff.piapi.contract.v1.TrailingStopStatus trailingStopStatus
    ) {
        return switch (trailingStopStatus) {
            case TRAILING_STOP_UNSPECIFIED -> TrailingStopStatus.UNSPECIFIED;
            case TRAILING_STOP_ACTIVE -> TrailingStopStatus.ACTIVE;
            case TRAILING_STOP_ACTIVATED -> TrailingStopStatus.ACTIVATED;
            case UNRECOGNIZED -> null;
        };
    }
}
