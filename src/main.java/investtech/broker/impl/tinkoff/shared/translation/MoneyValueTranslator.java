package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.value.money.Money;
import investtech.broker.contract.value.quotation.Quotation;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

public class MoneyValueTranslator {
    public static MoneyValue toTinkoff(Money money) {
        return MoneyValue.newBuilder()
                .setCurrency(money.getCurrencyIso())
                .setNano(money.getQuotation().getNano())
                .setUnits(money.getQuotation().getUnits())
                .build();
    }

    public static Money toContract(MoneyValue money) {
        return Money.of(money.getCurrency(), Quotation.of(money.getUnits(), money.getNano()));
    }
}
