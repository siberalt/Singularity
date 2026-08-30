package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffServices;
import com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffServicesFactory;
import ru.tinkoff.piapi.contract.v1.SandboxServiceGrpc;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;

public class TinkoffSandboxBrokerFactory {
    public static TinkoffSandboxBroker create(ConnectorConfiguration configuration) {
        return create(configuration, new TinkoffServicesFactory());
    }

    public static TinkoffSandboxBroker create(ConnectorConfiguration configuration, TinkoffServicesFactory servicesFactory) {
        TinkoffServices services = servicesFactory.create(configuration);
        TinkoffSandboxService sandboxService = new TinkoffSandboxService(
            SandboxServiceGrpc.newBlockingStub(services.serviceStubFactory().getChannel())
        );

        return new TinkoffSandboxBroker(services, sandboxService);
    }
}
