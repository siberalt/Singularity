package com.siberalt.singularity.broker.impl.tinkoff.execution;

import com.siberalt.singularity.broker.impl.tinkoff.shared.AbstractTinkoffBroker;
import ru.tinkoff.piapi.core.InvestApi;

public class TinkoffReadonlyBroker extends AbstractTinkoffBroker {
    public TinkoffReadonlyBroker(String token) {
        super(token);
    }

    @Override
    protected InvestApi createApi(String token) {
        return InvestApi.createReadonly(token);
    }
}
