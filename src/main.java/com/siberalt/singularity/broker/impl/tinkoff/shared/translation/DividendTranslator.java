package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.instrument.common.Dividend;

public class DividendTranslator {
    public static Dividend toContract(ru.tinkoff.piapi.contract.v1.Dividend dividend) {
        return new Dividend()
            .setDividendNet(MoneyValueTranslator.toContract(dividend.getDividendNet()))
            .setPaymentDate(TimestampTranslator.toContract(dividend.getPaymentDate()))
            .setDeclaredDate(TimestampTranslator.toContract(dividend.getDeclaredDate()))
            .setLastBuyDate(TimestampTranslator.toContract(dividend.getLastBuyDate()))
            .setDividendType(dividend.getDividendType())
            .setRecordDate(TimestampTranslator.toContract(dividend.getRecordDate()))
            .setRegularity(dividend.getRegularity())
            .setClosePrice(MoneyValueTranslator.toContract(dividend.getClosePrice()))
            .setYieldValue(QuotationTranslator.toContract(dividend.getYieldValue()))
            .setCreatedAt(TimestampTranslator.toContract(dividend.getCreatedAt()));
    }
}
