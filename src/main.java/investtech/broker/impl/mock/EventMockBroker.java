package investtech.broker.impl.mock;

import investtech.simulation.shared.instrument.InstrumentStorageInterface;
import investtech.simulation.shared.market.candle.CandleStorageInterface;

public class EventMockBroker extends MockBroker {
    protected EventMockOrderService orderService;

    public EventMockBroker(CandleStorageInterface candleRepository, InstrumentStorageInterface instrumentStorage) {
        super(candleRepository, instrumentStorage);
        this.orderService = new EventMockOrderService(this);
    }

    @Override
    public EventMockOrderService getOrderService() {
        return orderService;
    }
}
