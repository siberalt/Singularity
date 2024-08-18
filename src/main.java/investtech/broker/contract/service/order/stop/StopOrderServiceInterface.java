package investtech.broker.contract.service.order.stop;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.order.stop.request.CancelStopOrderRequest;
import investtech.broker.contract.service.order.stop.request.GetStopOrdersRequest;
import investtech.broker.contract.service.order.stop.request.PostStopOrderRequest;
import investtech.broker.contract.service.order.stop.response.CancelStopOrderResponse;
import investtech.broker.contract.service.order.stop.response.GetStopOrdersResponse;
import investtech.broker.contract.service.order.stop.response.PostStopOrderResponse;

public interface StopOrderServiceInterface {
    PostStopOrderResponse post(PostStopOrderRequest request) throws AbstractException;

    GetStopOrdersResponse get(GetStopOrdersRequest request) throws AbstractException;

    CancelStopOrderResponse cancel(CancelStopOrderRequest request) throws AbstractException;
}
