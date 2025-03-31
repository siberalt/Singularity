package com.siberalt.singularity.broker.common;

import com.siberalt.singularity.broker.contract.execution.SandboxServiceAwareBroker;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxMoneyManager;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxPositionManager;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxService;
import com.siberalt.singularity.broker.contract.value.money.Money;

public class SandboxBrokerFacade extends BrokerFacade {
    public SandboxBrokerFacade(SandboxServiceAwareBroker brokerFacade) {
        super(brokerFacade);
    }

    public void addToPosition(String accountId, String instrumentUid, long amount) {
        SandboxService sandboxService = ((SandboxServiceAwareBroker) broker).getSandboxService();

        if (sandboxService instanceof SandboxPositionManager) {
            ((SandboxPositionManager) sandboxService).addToPosition(accountId, instrumentUid, amount);
        } else {
            throw new UnsupportedOperationException("Sandbox service does not support position management");
        }
    }

    public String openAccount(String name) {
        return ((SandboxServiceAwareBroker) broker).getSandboxService().openAccount(name);
    }

    public void payIn(String accountId, Money moneyValue) {
        SandboxService sandboxService = ((SandboxServiceAwareBroker) broker).getSandboxService();

        if (sandboxService instanceof SandboxMoneyManager) {
            ((SandboxMoneyManager) sandboxService).payIn(accountId, moneyValue);
        } else {
            throw new UnsupportedOperationException("Sandbox service does not support money management");
        }
    }

    public void closeAccount(String accountId) {
        ((SandboxServiceAwareBroker) broker).getSandboxService().closeAccount(accountId);
    }

    public static SandboxBrokerFacade of(SandboxServiceAwareBroker broker) {
        return new SandboxBrokerFacade(broker);
    }
}
