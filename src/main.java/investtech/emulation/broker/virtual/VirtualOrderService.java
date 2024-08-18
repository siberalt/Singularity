package investtech.emulation.broker.virtual;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.market.request.GetCandlesRequest;
import investtech.broker.contract.service.order.OrderServiceInterface;
import investtech.broker.contract.service.order.request.*;
import investtech.broker.contract.service.order.response.CancelOrderResponse;
import investtech.broker.contract.service.order.response.GetOrdersResponse;
import investtech.broker.contract.service.order.response.OrderState;
import investtech.broker.contract.service.order.response.PostOrderResponse;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.ContextAwareInterface;

public class VirtualOrderService implements OrderServiceInterface, ContextAwareInterface {
    protected VirtualBroker virtualBroker;

    protected AbstractContext<?> context;

    public VirtualOrderService(VirtualBroker virtualBroker) {
        this.virtualBroker = virtualBroker;
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        virtualBroker.context.getCurrentTime();

        return null;
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        return null;
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        return null;
    }

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        return null;
    }

    @Override
    public PostOrderResponse replace(ReplaceOrderRequest request) throws AbstractException {
        return null;
    }

    @Override
    public void applyContext(AbstractContext<?> context) {
        this.context = context;
    }
}
