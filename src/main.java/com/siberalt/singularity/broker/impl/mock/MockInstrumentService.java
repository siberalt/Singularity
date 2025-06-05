package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetResponse;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;

public class MockInstrumentService implements InstrumentService {
    protected ReadInstrumentRepository instrumentStorage;

    protected MockBroker virtualBroker;

    public MockInstrumentService(MockBroker virtualBroker, ReadInstrumentRepository instrumentStorage) {
        this.instrumentStorage = instrumentStorage;
        this.virtualBroker = virtualBroker;
    }

    @Override
    public GetResponse get(GetRequest request) throws AbstractException {
        return new GetResponse().setInstrument(instrumentStorage.get(request.getId()));
    }
}
