package investtech.broker.impl.mock;

import investtech.simulation.shared.instrument.InstrumentStorageInterface;
import investtech.simulation.shared.market.candle.CandleStorageInterface;

public class StateMockBroker extends MockBroker {
    protected StateMockOrderService orderService;

    public StateMockBroker(CandleStorageInterface candleRepository, InstrumentStorageInterface instrumentStorage) {
        super(candleRepository, instrumentStorage);
        this.orderService = new StateMockOrderService(this);
    }

    @Override
    public StateMockOrderService getOrderService() {
        return orderService;
    }
}
