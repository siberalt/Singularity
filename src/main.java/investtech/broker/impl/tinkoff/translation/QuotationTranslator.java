package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.value.quatation.Quotation;

public class QuotationTranslator {
    public static ru.tinkoff.piapi.contract.v1.Quotation toTinkoff(Quotation quotation) {
        return ru.tinkoff.piapi.contract.v1.Quotation.newBuilder()
                .setNano(quotation.getNano())
                .setUnits(quotation.getUnits())
                .build();
    }

    public static Quotation toContract(ru.tinkoff.piapi.contract.v1.Quotation quotation) {
        return new Quotation()
                .setNano(quotation.getNano())
                .setUnits(quotation.getUnits());
    }
}
