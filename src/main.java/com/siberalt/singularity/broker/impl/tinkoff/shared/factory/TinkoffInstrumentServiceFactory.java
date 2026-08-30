package com.siberalt.singularity.broker.impl.tinkoff.shared.factory;

import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

public class TinkoffInstrumentServiceFactory implements TinkoffServiceFactory<InstrumentService> {
    @Override
    public InstrumentService create(ServiceStubFactory serviceStubFactory) {
        return new com.siberalt.singularity.broker.impl.tinkoff.shared.InstrumentService(
            serviceStubFactory.newSyncService(InstrumentsServiceGrpc::newBlockingStub).getStub()
        );
    }
}
