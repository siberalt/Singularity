package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetTradableRequest;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetResponse;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetTradableResponse;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;

import java.util.List;
import java.util.stream.Collectors;

public class MockInstrumentService implements InstrumentService {
    protected ReadInstrumentRepository instrumentStorage;

    protected MockBroker virtualBroker;

    public MockInstrumentService(MockBroker virtualBroker, ReadInstrumentRepository instrumentStorage) {
        this.instrumentStorage = instrumentStorage;
        this.virtualBroker = virtualBroker;
    }

    @Override
    public GetResponse get(GetRequest request) throws AbstractException {
        return new GetResponse().setInstrument(
            instrumentStorage.get(virtualBroker.getId(), request.getId()).orElse(null)
        );
    }

    @Override
    public GetTradableResponse getTradable(GetTradableRequest request) throws AbstractException {
        List<Instrument> instruments = instrumentStorage.getAll(virtualBroker.getId()).stream()
            .filter(instrument -> request.getCurrency() == null || instrument.getCurrency().equalsIgnoreCase(request.getCurrency()))
            .collect(Collectors.toList());

        return new GetTradableResponse().setInstruments(instruments);
    }
}
