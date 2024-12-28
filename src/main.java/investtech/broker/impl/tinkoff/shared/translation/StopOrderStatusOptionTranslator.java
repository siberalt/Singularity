package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.service.order.stop.common.StopOrderStatusOption;

public class StopOrderStatusOptionTranslator {
    public static ru.tinkoff.piapi.contract.v1.StopOrderStatusOption toTinkoff(
            StopOrderStatusOption stopOrderStatusOption
    ) {
        return switch (stopOrderStatusOption) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.StopOrderStatusOption.STOP_ORDER_STATUS_UNSPECIFIED;
            case ALL -> ru.tinkoff.piapi.contract.v1.StopOrderStatusOption.STOP_ORDER_STATUS_ALL;
            case ACTIVE -> ru.tinkoff.piapi.contract.v1.StopOrderStatusOption.STOP_ORDER_STATUS_ACTIVE;
            case EXECUTED -> ru.tinkoff.piapi.contract.v1.StopOrderStatusOption.STOP_ORDER_STATUS_EXECUTED;
            case CANCELED -> ru.tinkoff.piapi.contract.v1.StopOrderStatusOption.STOP_ORDER_STATUS_CANCELED;
            case EXPIRED -> ru.tinkoff.piapi.contract.v1.StopOrderStatusOption.STOP_ORDER_STATUS_EXPIRED;
        };
    }

    public static StopOrderStatusOption toContract(
            ru.tinkoff.piapi.contract.v1.StopOrderStatusOption stopOrderStatusOption
    ) {
        return switch (stopOrderStatusOption) {
            case STOP_ORDER_STATUS_UNSPECIFIED -> StopOrderStatusOption.UNSPECIFIED;
            case STOP_ORDER_STATUS_ALL -> StopOrderStatusOption.ALL;
            case STOP_ORDER_STATUS_ACTIVE -> StopOrderStatusOption.ACTIVE;
            case STOP_ORDER_STATUS_EXECUTED -> StopOrderStatusOption.EXECUTED;
            case STOP_ORDER_STATUS_CANCELED -> StopOrderStatusOption.CANCELED;
            case STOP_ORDER_STATUS_EXPIRED -> StopOrderStatusOption.EXPIRED;
            case UNRECOGNIZED -> null;
        };
    }
}
