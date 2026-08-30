package com.siberalt.singularity.broker.impl.tinkoff.shared.factory;

import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import ru.tinkoff.piapi.contract.v1.OperationsServiceGrpc;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

public class TinkoffOperationsServiceFactory implements TinkoffServiceFactory<OperationsService> {
    @Override
    public OperationsService create(ServiceStubFactory serviceStubFactory) {
        return new com.siberalt.singularity.broker.impl.tinkoff.shared.OperationsService(
            serviceStubFactory.newSyncService(OperationsServiceGrpc::newBlockingStub).getStub()
        );
    }
}
