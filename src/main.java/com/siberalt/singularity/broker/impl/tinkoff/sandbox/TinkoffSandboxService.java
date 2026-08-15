package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.sandbox.SandboxMoneyManager;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxService;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.MoneyValueTranslator;
import ru.tinkoff.piapi.contract.v1.CloseSandboxAccountRequest;
import ru.tinkoff.piapi.contract.v1.OpenSandboxAccountRequest;
import ru.tinkoff.piapi.contract.v1.SandboxPayInRequest;
import ru.tinkoff.piapi.contract.v1.SandboxServiceGrpc.SandboxServiceBlockingStub;

public class TinkoffSandboxService implements SandboxService, SandboxMoneyManager {
    private final SandboxServiceBlockingStub api;

    public TinkoffSandboxService(SandboxServiceBlockingStub api) {
        this.api = api;
    }

    @Override
    public String openAccount(String name) {
        return api.openSandboxAccount(OpenSandboxAccountRequest.newBuilder().setName(name).build()).getAccountId();
    }

    @Override
    public void payIn(String accountId, Money moneyValue) {
        api.sandboxPayIn(SandboxPayInRequest.newBuilder()
            .setAccountId(accountId)
            .setAmount(MoneyValueTranslator.toTinkoff(moneyValue))
            .build());
    }

    public void closeAccount(String accountId) {
        api.closeSandboxAccount(CloseSandboxAccountRequest.newBuilder().setAccountId(accountId).build());
    }
}
