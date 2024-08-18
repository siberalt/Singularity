package investtech.broker.contract.service.operation;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.operation.request.GetPositionsRequest;
import investtech.broker.contract.service.operation.response.GetPositionsResponse;

public interface OperationsServiceInterface {
    GetPositionsResponse getPositions(GetPositionsRequest request) throws AbstractException;
}
