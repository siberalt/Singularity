package com.siberalt.singularity.broker.impl.mock;

public class StateMockOrderService extends MockOrderService {
    protected StateMockBroker mockBroker;

    public StateMockOrderService(StateMockBroker mockBroker) {
        super(mockBroker);
        this.mockBroker = mockBroker;
    }


}
