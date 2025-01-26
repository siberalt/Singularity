package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;

public class OrderDirectionTranslator {
    public static ru.tinkoff.piapi.contract.v1.OrderDirection toTinkoff(OrderDirection orderDirection) {
        return switch (orderDirection) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_UNSPECIFIED;
            case BUY -> ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_BUY;
            case SELL -> ru.tinkoff.piapi.contract.v1.OrderDirection.ORDER_DIRECTION_SELL;
        };
    }

    public static OrderDirection toContract(ru.tinkoff.piapi.contract.v1.OrderDirection orderDirection) {
        return switch (orderDirection) {
            case ORDER_DIRECTION_UNSPECIFIED -> OrderDirection.UNSPECIFIED;
            case ORDER_DIRECTION_BUY -> OrderDirection.BUY;
            case ORDER_DIRECTION_SELL -> OrderDirection.SELL;
            case UNRECOGNIZED -> null;
        };
    }
}
