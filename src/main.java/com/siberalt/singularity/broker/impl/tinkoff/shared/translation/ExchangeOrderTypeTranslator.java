package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.order.common.ExchangeOrderType;

public class ExchangeOrderTypeTranslator {
    public static ru.tinkoff.piapi.contract.v1.ExchangeOrderType toTinkoff(ExchangeOrderType exchangeOrderType) {
        return switch (exchangeOrderType) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.ExchangeOrderType.EXCHANGE_ORDER_TYPE_UNSPECIFIED;
            case LIMIT -> ru.tinkoff.piapi.contract.v1.ExchangeOrderType.EXCHANGE_ORDER_TYPE_LIMIT;
            case MARKET -> ru.tinkoff.piapi.contract.v1.ExchangeOrderType.EXCHANGE_ORDER_TYPE_MARKET;
        };
    }

    public static ExchangeOrderType toContract(ru.tinkoff.piapi.contract.v1.ExchangeOrderType exchangeOrderType) {
        return switch (exchangeOrderType) {
            case EXCHANGE_ORDER_TYPE_UNSPECIFIED -> ExchangeOrderType.UNSPECIFIED;
            case EXCHANGE_ORDER_TYPE_LIMIT -> ExchangeOrderType.LIMIT;
            case EXCHANGE_ORDER_TYPE_MARKET -> ExchangeOrderType.MARKET;
            case UNRECOGNIZED -> null;
        };
    }
}
