package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxMoneyManager;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxPositionManager;
import com.siberalt.singularity.broker.contract.service.user.AccessLevel;
import com.siberalt.singularity.broker.contract.service.user.AccountType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxService;

public class MockSandboxService implements SandboxService, SandboxMoneyManager, SandboxPositionManager {
    private final MockUserService userService;
    private final MockOperationsService operationsService;

    public MockSandboxService(MockUserService userService, MockOperationsService operationsService) {
        this.userService = userService;
        this.operationsService = operationsService;
    }

    @Override
    public String openAccount(String name) {
        return userService.openAccount(name, AccountType.ORDINARY, AccessLevel.FULL_ACCESS).getId();
    }

    @Override
    public void payIn(String accountId, Money money) {
        try {
            operationsService.addMoney(accountId, money);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void closeAccount(String accountId) {
        try {
            userService.closeAccount(accountId);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addToPosition(String accountId, String instrumentUid, long amount) {
        try {
            operationsService.addToPosition(accountId, instrumentUid, amount);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }
}
