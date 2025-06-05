package com.siberalt.singularity.broker.contract.service.operation;

import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.contract.service.operation.response.GetPositionsResponse;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;

public interface OperationsService {
    GetPositionsResponse getPositions(GetPositionsRequest request) throws AbstractException;
}
