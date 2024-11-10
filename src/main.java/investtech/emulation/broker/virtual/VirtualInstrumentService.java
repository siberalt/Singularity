package investtech.emulation.broker.virtual;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.instrument.InstrumentServiceInterface;
import investtech.broker.contract.service.instrument.request.GetRequest;
import investtech.broker.contract.service.instrument.response.GetResponse;
import investtech.emulation.shared.instrument.InstrumentStorageInterface;

public class VirtualInstrumentService implements InstrumentServiceInterface {
    protected InstrumentStorageInterface instrumentStorage;

    protected VirtualBroker virtualBroker;

    public VirtualInstrumentService(VirtualBroker virtualBroker, InstrumentStorageInterface instrumentStorage) {
        this.instrumentStorage = instrumentStorage;
        this.virtualBroker = virtualBroker;
    }

    @Override
    public GetResponse get(GetRequest request) throws AbstractException {
        return new GetResponse().setInstrument(instrumentStorage.get(request.getId()));
    }
}
