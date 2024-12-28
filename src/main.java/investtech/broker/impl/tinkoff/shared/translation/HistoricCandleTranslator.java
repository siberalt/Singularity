package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.service.market.response.HistoricCandle;

public class HistoricCandleTranslator {
    public static ru.tinkoff.piapi.contract.v1.HistoricCandle toTinkoff(HistoricCandle candle) {
        return ru.tinkoff.piapi.contract.v1.HistoricCandle.newBuilder()
                .setOpen(QuotationTranslator.toTinkoff(candle.getOpen()))
                .setHigh(QuotationTranslator.toTinkoff(candle.getHigh()))
                .setLow(QuotationTranslator.toTinkoff(candle.getLow()))
                .setClose(QuotationTranslator.toTinkoff(candle.getClose()))
                .setVolume(candle.getVolume())
                .setTime(TimestampTranslator.toTinkoff(candle.getTime()))
                .setIsComplete(candle.isComplete())
                .build();
    }

    public static HistoricCandle toContract(ru.tinkoff.piapi.contract.v1.HistoricCandle candle) {
        return new HistoricCandle()
                .setOpen(QuotationTranslator.toContract(candle.getOpen()))
                .setHigh(QuotationTranslator.toContract(candle.getHigh()))
                .setLow(QuotationTranslator.toContract(candle.getLow()))
                .setClose(QuotationTranslator.toContract(candle.getClose()))
                .setVolume(candle.getVolume())
                .setTime(TimestampTranslator.toContract(candle.getTime()))
                .setComplete(candle.getIsComplete());
    }
}
