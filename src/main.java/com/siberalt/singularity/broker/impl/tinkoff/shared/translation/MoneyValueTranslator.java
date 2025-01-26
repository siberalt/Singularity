package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
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
        if (money.getCurrency().isEmpty()) {
            return null;
        }

        return Money.of(money.getCurrency(), Quotation.of(money.getUnits(), money.getNano()));
    }
}
