package investtech.broker.impl.mock.simulation;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.instrument.InstrumentServiceInterface;
import investtech.broker.contract.service.instrument.request.GetRequest;
import investtech.broker.contract.service.instrument.response.GetResponse;
import investtech.simulation.shared.instrument.InstrumentStorageInterface;

public class MockInstrumentService implements InstrumentServiceInterface {
    protected InstrumentStorageInterface instrumentStorage;

    protected MockBroker virtualBroker;

    public MockInstrumentService(MockBroker virtualBroker, InstrumentStorageInterface instrumentStorage) {
        this.instrumentStorage = instrumentStorage;
        this.virtualBroker = virtualBroker;
    }

    @Override
    public GetResponse get(GetRequest request) throws AbstractException {
        return new GetResponse().setInstrument(instrumentStorage.get(request.getId()));
    }
}
