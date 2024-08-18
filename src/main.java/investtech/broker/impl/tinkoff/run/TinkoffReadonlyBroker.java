package investtech.broker.impl.tinkoff.run;

import investtech.broker.impl.tinkoff.AbstractTinkoffBroker;
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
