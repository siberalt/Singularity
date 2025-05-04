package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.entity.order.OrderRepository;

public class StateMockOrderService extends MockOrderService {
    protected StateMockBroker mockBroker;

    public StateMockOrderService(StateMockBroker mockBroker, OrderRepository orderRepository) {
        super(mockBroker, orderRepository);
        this.mockBroker = mockBroker;
    }
}
