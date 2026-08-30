package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.execution.SandboxServiceAwareBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffServices;

public class TinkoffSandboxBroker extends AbstractTinkoffBroker implements SandboxServiceAwareBroker {
    private final TinkoffSandboxService sandboxService;

    public TinkoffSandboxBroker(TinkoffServices services, TinkoffSandboxService sandboxService) {
        super(services);
        this.sandboxService = sandboxService;
    }

    @Override
    public TinkoffSandboxService getSandboxService() {
        return sandboxService;
    }
}
