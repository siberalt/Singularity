package com.siberalt.singularity.broker.contract.service.order.stop.response;


import java.util.List;

public class GetStopOrdersResponse {
    List<StopOrder> stopOrders;

    public List<StopOrder> getStopOrders() {
        return stopOrders;
    }

    public GetStopOrdersResponse setStopOrders(List<StopOrder> stopOrders) {
        this.stopOrders = stopOrders;
        return this;
    }
}
