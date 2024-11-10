package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.value.money.Money;
import investtech.broker.contract.value.quatation.Quotation;

public class MoneyTranslator {
    public static ru.tinkoff.piapi.core.models.Money toTinkoff(Money money) {
        return ru.tinkoff.piapi.core.models.Money.builder()
                .currency(money.getCurrencyIso())
                .value(money.getQuotation().toBigDecimal())
                .build();
    }

    public static Money toContract(ru.tinkoff.piapi.core.models.Money money) {
        return Money.of(money.getCurrency(), Quotation.of(money.getValue()));
    }
}
