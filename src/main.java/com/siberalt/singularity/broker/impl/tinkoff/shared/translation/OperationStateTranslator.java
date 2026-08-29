package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.entity.operation.OperationState;

public class OperationStateTranslator {
    public static OperationState toContract(ru.tinkoff.piapi.contract.v1.OperationState operationState) {
        return switch (operationState) {
            case OPERATION_STATE_UNSPECIFIED, UNRECOGNIZED -> OperationState.UNSPECIFIED;
            case OPERATION_STATE_EXECUTED -> OperationState.EXECUTED;
            case OPERATION_STATE_CANCELED -> OperationState.CANCELED;
            case OPERATION_STATE_PROGRESS -> OperationState.PROGRESS;
        };
    }
}
