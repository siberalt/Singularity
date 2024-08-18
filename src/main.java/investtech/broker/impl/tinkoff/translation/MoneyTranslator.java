package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.value.money.Money;

import java.math.BigDecimal;

public class MoneyTranslator {
    public static ru.tinkoff.piapi.core.models.Money toTinkoff(Money money) {
        return ru.tinkoff.piapi.core.models.Money.builder()
                .currency(money.getCurrencyIso())
                .value(new BigDecimal(money.getUnits()))
                .build();
    }

    public static Money toContract(ru.tinkoff.piapi.core.models.Money money) {
        return new Money()
                .setCurrencyIso(money.getCurrency())
                .setNano(0)
                .setUnits(money.getValue().longValue());
    }
}
