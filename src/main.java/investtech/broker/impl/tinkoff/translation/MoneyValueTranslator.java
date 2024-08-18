package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.value.money.Money;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

public class MoneyValueTranslator {
    public static MoneyValue toTinkoff(Money money) {
        return MoneyValue.newBuilder()
                .setCurrency(money.getCurrencyIso())
                .setNano(money.getNano())
                .setUnits(money.getUnits())
                .build();
    }

    public static Money toContract(MoneyValue money) {
        return new Money()
                .setCurrencyIso(money.getCurrency())
                .setNano(money.getNano())
                .setUnits(money.getUnits());
    }
}
