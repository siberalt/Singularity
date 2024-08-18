package investtech.broker.contract.service.order.response;

import java.util.Collection;

public class GetOrdersResponse {
    Collection<OrderState> orders;

    public Collection<OrderState> getOrders() {
        return orders;
    }

    public GetOrdersResponse setOrders(Collection<OrderState> orders) {
        this.orders = orders;
        return this;
    }
}
