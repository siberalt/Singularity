package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.order.stop.request.StopOrderType;

public class StopOrderTypeTranslator {
    public static ru.tinkoff.piapi.contract.v1.StopOrderType toTinkoff(StopOrderType stopOrderType) {
        return switch (stopOrderType) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.StopOrderType.STOP_ORDER_TYPE_UNSPECIFIED;
            case TAKE_PROFIT -> ru.tinkoff.piapi.contract.v1.StopOrderType.STOP_ORDER_TYPE_TAKE_PROFIT;
            case STOP_LOSS -> ru.tinkoff.piapi.contract.v1.StopOrderType.STOP_ORDER_TYPE_STOP_LOSS;
            case STOP_LIMIT -> ru.tinkoff.piapi.contract.v1.StopOrderType.STOP_ORDER_TYPE_STOP_LIMIT;
        };
    }

    public static StopOrderType toContract(ru.tinkoff.piapi.contract.v1.StopOrderType stopOrderType) {
        return switch (stopOrderType) {
            case STOP_ORDER_TYPE_UNSPECIFIED -> StopOrderType.UNSPECIFIED;
            case STOP_ORDER_TYPE_TAKE_PROFIT -> StopOrderType.TAKE_PROFIT;
            case STOP_ORDER_TYPE_STOP_LOSS -> StopOrderType.STOP_LOSS;
            case STOP_ORDER_TYPE_STOP_LIMIT -> StopOrderType.STOP_LIMIT;
            case UNRECOGNIZED -> null;
        };
    }
}
