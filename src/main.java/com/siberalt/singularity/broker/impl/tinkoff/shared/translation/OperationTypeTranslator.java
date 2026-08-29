package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.entity.operation.OperationType;

public class OperationTypeTranslator {
    /**
     * Tinkoff's real OperationType has ~50 values (dividends, taxes, coupons, transfers, etc.).
     * Our OperationType only models the subset actually produced by the mock broker so far
     * (plain buys/sells, their margin variants, delivery, and the fee types). Everything else
     * maps to UNSPECIFIED until a concrete use case needs it modeled explicitly.
     */
    public static OperationType toContract(ru.tinkoff.piapi.contract.v1.OperationType operationType) {
        return switch (operationType) {
            case OPERATION_TYPE_BUY -> OperationType.BUY;
            case OPERATION_TYPE_SELL -> OperationType.SELL;
            case OPERATION_TYPE_BUY_MARGIN -> OperationType.BUY_MARGIN;
            case OPERATION_TYPE_SELL_MARGIN -> OperationType.SELL_MARGIN;
            case OPERATION_TYPE_DELIVERY_BUY -> OperationType.DELIVERY_BUY;
            case OPERATION_TYPE_DELIVERY_SELL -> OperationType.DELIVERY_SELL;
            case OPERATION_TYPE_BROKER_FEE -> OperationType.BROKER_FEE;
            case OPERATION_TYPE_SERVICE_FEE -> OperationType.SERVICE_FEE;
            case OPERATION_TYPE_MARGIN_FEE -> OperationType.MARGIN_FEE;
            default -> OperationType.UNSPECIFIED;
        };
    }
}
