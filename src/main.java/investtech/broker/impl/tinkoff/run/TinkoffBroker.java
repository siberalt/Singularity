package investtech.broker.impl.tinkoff.run;

import investtech.broker.contract.run.StopOrderServiceAwareBrokerInterface;
import investtech.broker.contract.service.order.stop.StopOrderServiceInterface;
import investtech.broker.impl.tinkoff.AbstractTinkoffBroker;
import investtech.broker.impl.tinkoff.StopOrderService;

public class TinkoffBroker extends AbstractTinkoffBroker implements StopOrderServiceAwareBrokerInterface {
    protected StopOrderServiceInterface stopOrderService;

    public TinkoffBroker(String token) {
        super(token);
    }

    @Override
    protected void init(String token) {
        super.init(token);
        stopOrderService = new StopOrderService(api.getStopOrdersService());
    }

    @Override
    public StopOrderServiceInterface getStopOrderService() {
        return stopOrderService;
    }
}
