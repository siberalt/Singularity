package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.service.market.response.LastPrice;

public class LastPriceTranslator {
    public static ru.tinkoff.piapi.contract.v1.LastPrice toTinkoff(LastPrice lastPrice) {
        return ru.tinkoff.piapi.contract.v1.LastPrice.newBuilder()
                .setPrice(QuotationTranslator.toTinkoff(lastPrice.getPrice()))
                .setTime(TimestampTranslator.toTinkoff(lastPrice.getTime()))
                .setInstrumentUid(lastPrice.getInstrumentUid())
                .build();
    }

    public static LastPrice toContract(ru.tinkoff.piapi.contract.v1.LastPrice lastPrice) {
        return new LastPrice()
                .setPrice(QuotationTranslator.toContract(lastPrice.getPrice()))
                .setTime(TimestampTranslator.toContract(lastPrice.getTime()))
                .setInstrumentUid(lastPrice.getInstrumentUid());
    }
}
