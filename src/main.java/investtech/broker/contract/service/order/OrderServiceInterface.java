package investtech.broker.contract.service.order;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.order.request.*;
import investtech.broker.contract.service.order.response.*;

public interface OrderServiceInterface {
    PostOrderResponse post(PostOrderRequest request) throws AbstractException;

    CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException;

    OrderState getState(GetOrderStateRequest request) throws AbstractException;

    GetOrdersResponse get(GetOrdersRequest request) throws AbstractException;
}
