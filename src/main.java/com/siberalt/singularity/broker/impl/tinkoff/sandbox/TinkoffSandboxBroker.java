package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.core.InvestApi;

public class TinkoffSandboxBroker extends AbstractTinkoffBroker {
    public TinkoffSandboxBroker(String token) {
        super(token);
    }

    public String openAccount() {
        return api.getSandboxService().openAccountSync();
    }

    public MoneyValue payIn(String accountId, MoneyValue moneyValue) {
        return api.getSandboxService().payInSync(accountId, moneyValue);
    }

    public void closeAccount(String accountId) {
        api.getSandboxService().closeAccountSync(accountId);
    }

    @Override
    protected InvestApi createApi(String token) {
        return InvestApi.createSandbox(token);
    }
}
