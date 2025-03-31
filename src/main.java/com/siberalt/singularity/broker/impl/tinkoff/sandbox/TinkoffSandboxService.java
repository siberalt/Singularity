package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.sandbox.SandboxMoneyManager;
import com.siberalt.singularity.broker.contract.service.sandbox.SandboxService;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.MoneyValueTranslator;

public class TinkoffSandboxService implements SandboxService, SandboxMoneyManager {
    private final ru.tinkoff.piapi.core.SandboxService api;

    public TinkoffSandboxService(ru.tinkoff.piapi.core.SandboxService api) {
        this.api = api;
    }

    @Override
    public String openAccount(String name) {
        return api.openAccountSync(name);
    }

    @Override
    public void payIn(String accountId, Money moneyValue) {
        api.payInSync(accountId, MoneyValueTranslator.toTinkoff(moneyValue));
    }

    public void closeAccount(String accountId) {
        api.closeAccountSync(accountId);
    }
}
