package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.order.stop.request.StopOrderExpirationType;

public class StopOrderExpirationTypeTranslator {
    public static ru.tinkoff.piapi.contract.v1.StopOrderExpirationType toTinkoff(StopOrderExpirationType expirationType) {
        return switch (expirationType) {
            case UNSPECIFIED ->
                    ru.tinkoff.piapi.contract.v1.StopOrderExpirationType.STOP_ORDER_EXPIRATION_TYPE_UNSPECIFIED;
            case GOOD_TILL_CANCEL ->
                    ru.tinkoff.piapi.contract.v1.StopOrderExpirationType.STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_CANCEL;
            case GOOD_TILL_DATE ->
                    ru.tinkoff.piapi.contract.v1.StopOrderExpirationType.STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_DATE;
        };
    }

    public static StopOrderExpirationType toContract(ru.tinkoff.piapi.contract.v1.StopOrderExpirationType stopOrderExpirationType){
        return switch (stopOrderExpirationType) {
            case STOP_ORDER_EXPIRATION_TYPE_UNSPECIFIED -> StopOrderExpirationType.UNSPECIFIED;
            case STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_CANCEL -> StopOrderExpirationType.GOOD_TILL_CANCEL;
            case STOP_ORDER_EXPIRATION_TYPE_GOOD_TILL_DATE -> StopOrderExpirationType.GOOD_TILL_DATE;
            case UNRECOGNIZED -> null;
        };
    }
}
