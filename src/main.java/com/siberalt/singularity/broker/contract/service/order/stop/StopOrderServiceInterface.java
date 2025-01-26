package com.siberalt.singularity.broker.contract.service.order.stop;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.stop.request.CancelStopOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.stop.request.GetStopOrdersRequest;
import com.siberalt.singularity.broker.contract.service.order.stop.request.PostStopOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.stop.response.CancelStopOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.stop.response.GetStopOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.stop.response.PostStopOrderResponse;

public interface StopOrderServiceInterface {
    PostStopOrderResponse post(PostStopOrderRequest request) throws AbstractException;

    GetStopOrdersResponse get(GetStopOrdersRequest request) throws AbstractException;

    CancelStopOrderResponse cancel(CancelStopOrderRequest request) throws AbstractException;
}
