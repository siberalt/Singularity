package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.execution.SandboxServiceAwareBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import ru.tinkoff.piapi.contract.v1.SandboxServiceGrpc;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;

public class TinkoffSandboxBroker extends AbstractTinkoffBroker implements SandboxServiceAwareBroker {
    private final TinkoffSandboxService sandboxService;

    public TinkoffSandboxBroker(ConnectorConfiguration configuration) {
        super(configuration);
        sandboxService = new TinkoffSandboxService(SandboxServiceGrpc.newBlockingStub(serviceStubFactory.getChannel()));
    }

    @Override
    public TinkoffSandboxService getSandboxService() {
        return sandboxService;
    }
}
