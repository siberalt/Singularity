package com.siberalt.singularity.broker.contract.execution;

import com.siberalt.singularity.broker.contract.service.sandbox.SandboxService;

public interface SandboxServiceAwareBroker extends BrokerInterface {
    SandboxService getSandboxService();
}
