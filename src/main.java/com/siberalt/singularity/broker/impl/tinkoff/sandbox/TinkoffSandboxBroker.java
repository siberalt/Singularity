package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.execution.SandboxServiceAwareBroker;
import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import ru.tinkoff.piapi.core.InvestApi;

public class TinkoffSandboxBroker extends AbstractTinkoffBroker implements SandboxServiceAwareBroker {
    private final TinkoffSandboxService sandboxService;

    public TinkoffSandboxBroker(String token) {
        super(token);
        sandboxService = new TinkoffSandboxService(api.getSandboxService());
    }

    @Override
    protected InvestApi createApi(String token) {
        return InvestApi.createSandbox(token);
    }

    @Override
    public TinkoffSandboxService getSandboxService() {
        return sandboxService;
    }
}
