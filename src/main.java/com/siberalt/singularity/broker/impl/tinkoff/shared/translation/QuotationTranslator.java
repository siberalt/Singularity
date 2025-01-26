package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

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
