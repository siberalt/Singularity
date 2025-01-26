package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.order.request.OrderType;

public class OrderTypeTranslator {
    public static ru.tinkoff.piapi.contract.v1.OrderType toTinkoff(OrderType orderType) {
        return switch (orderType) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_UNSPECIFIED;
            case BEST_PRICE -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_BESTPRICE;
            case LIMIT -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_LIMIT;
            case MARKET -> ru.tinkoff.piapi.contract.v1.OrderType.ORDER_TYPE_MARKET;
        };
    }
    
    public static OrderType toContract(ru.tinkoff.piapi.contract.v1.OrderType orderType) {
        return switch (orderType) {
            case ORDER_TYPE_UNSPECIFIED -> OrderType.UNSPECIFIED;
            case ORDER_TYPE_BESTPRICE -> OrderType.BEST_PRICE;
            case ORDER_TYPE_LIMIT -> OrderType.LIMIT;
            case ORDER_TYPE_MARKET -> OrderType.MARKET;
            case UNRECOGNIZED -> null;
        };
    }
}
