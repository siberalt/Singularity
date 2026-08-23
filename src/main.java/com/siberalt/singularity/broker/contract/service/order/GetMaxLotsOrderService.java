package com.siberalt.singularity.broker.contract.service.order;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.request.GetMaxLotsRequest;
import com.siberalt.singularity.broker.contract.service.order.response.GetMaxLotsResponse;

/**
 * Service interface for getting maximum lots information.
 * Analogous to ru.tinkoff.piapi.contract.v1.OrdersServiceGrpc.OrdersServiceBlockingStub.getMaxLots
 */
public interface GetMaxLotsOrderService extends OrderService {
    /**
     * Gets the maximum number of lots and total amount for an order.
     *
     * @param request request containing accountId, instrumentId, quantity, and direction
     * @return response with lotsMax and amountMax
     */
    GetMaxLotsResponse getMaxLots(GetMaxLotsRequest request) throws AbstractException;
}
