package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.order.stop.request.StopOrderDirection;

public class StopOrderDirectionTranslator {
    public static ru.tinkoff.piapi.contract.v1.StopOrderDirection toTinkoff(StopOrderDirection stopOrderDirection) {
        return switch (stopOrderDirection) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.StopOrderDirection.STOP_ORDER_DIRECTION_UNSPECIFIED;
            case BUY -> ru.tinkoff.piapi.contract.v1.StopOrderDirection.STOP_ORDER_DIRECTION_BUY;
            case SELL -> ru.tinkoff.piapi.contract.v1.StopOrderDirection.STOP_ORDER_DIRECTION_SELL;
        };
    }

    public static StopOrderDirection toContract(ru.tinkoff.piapi.contract.v1.StopOrderDirection stopOrderDirection) {
        return switch (stopOrderDirection) {
            case STOP_ORDER_DIRECTION_UNSPECIFIED -> StopOrderDirection.UNSPECIFIED;
            case STOP_ORDER_DIRECTION_BUY -> StopOrderDirection.BUY;
            case STOP_ORDER_DIRECTION_SELL -> StopOrderDirection.SELL;
            case UNRECOGNIZED -> null;
        };
    }
}
