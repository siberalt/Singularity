package com.siberalt.singularity.broker.contract.service.order;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.*;

public interface OrderService {
    CalculateResponse calculate(CalculateRequest request) throws AbstractException;

    PostOrderResponse post(PostOrderRequest request) throws AbstractException;

    CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException;

    OrderState getState(GetOrderStateRequest request) throws AbstractException;

    GetOrdersResponse get(GetOrdersRequest request) throws AbstractException;
}
