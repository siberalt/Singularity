package com.siberalt.singularity.broker.impl.mock.config;

public class MockBrokerConfig {
    public static final String SETTINGS_PATH = "src/test/resources/broker.mock/test-settings.yaml";

    protected String brokerId = "mock";
    protected InstrumentConfig instrument;
    protected OrderServiceConfig orderService = new OrderServiceConfig();

    public OrderServiceConfig getOrderService() {
        return orderService;
    }

    public void setOrderService(OrderServiceConfig orderService) {
        this.orderService = orderService;
    }

    public InstrumentConfig getInstrument() {
        return instrument;
    }

    public void setInstrument(InstrumentConfig instrument) {
        this.instrument = instrument;
    }

    public String getBrokerId() {
        return brokerId;
    }

    public MockBrokerConfig setBrokerId(String brokerId) {
        this.brokerId = brokerId;
        return this;
    }
}
