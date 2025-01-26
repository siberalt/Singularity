package com.siberalt.singularity.broker.contract.service.order.response;

import java.util.List;

public class GetOrdersResponse {
    List<OrderState> orders;

    public List<OrderState> getOrders() {
        return orders;
    }

    public GetOrdersResponse setOrders(List<OrderState> orders) {
        this.orders = orders;
        return this;
    }
}
