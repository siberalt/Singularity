package com.siberalt.singularity.broker.contract.service.order;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.request.CancelOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetOrderStateRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetOrdersRequest;
import com.siberalt.singularity.broker.contract.service.order.request.PostOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.response.CancelOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.response.GetOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.response.OrderState;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;

public interface OrderServiceInterface {
    PostOrderResponse post(PostOrderRequest request) throws AbstractException;

    CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException;

    OrderState getState(GetOrderStateRequest request) throws AbstractException;

    GetOrdersResponse get(GetOrdersRequest request) throws AbstractException;
}
