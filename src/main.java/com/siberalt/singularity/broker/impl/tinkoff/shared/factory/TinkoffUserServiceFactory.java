package com.siberalt.singularity.broker.impl.tinkoff.shared.factory;

import com.siberalt.singularity.broker.contract.service.user.UserService;
import ru.tinkoff.piapi.contract.v1.UsersServiceGrpc;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

public class TinkoffUserServiceFactory implements TinkoffServiceFactory<UserService> {
    @Override
    public UserService create(ServiceStubFactory serviceStubFactory) {
        return new com.siberalt.singularity.broker.impl.tinkoff.shared.UserService(
            serviceStubFactory.newSyncService(UsersServiceGrpc::newBlockingStub).getStub()
        );
    }
}
