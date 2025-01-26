package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;

public class CandleIntervalTranslator {
    public static ru.tinkoff.piapi.contract.v1.CandleInterval toTinkoff(CandleInterval candleInterval) {
        return switch (candleInterval) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_UNSPECIFIED;
            case MIN_1 -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_1_MIN;
            case MIN_5 -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_5_MIN;
            case MIN_15 -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_15_MIN;
            case HOUR -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_HOUR;
            case DAY -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_DAY;
            case MIN_2 -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_2_MIN;
            case MIN_3 -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_3_MIN;
            case MIN_10 -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_10_MIN;
            case MIN_30 -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_30_MIN;
            case HOUR_2 -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_2_HOUR;
            case HOUR_4 -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_4_HOUR;
            case WEEK -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_WEEK;
            case MONTH -> ru.tinkoff.piapi.contract.v1.CandleInterval.CANDLE_INTERVAL_MONTH;
        };
    }

    public static CandleInterval toContract(ru.tinkoff.piapi.contract.v1.CandleInterval candleInterval) {
        return switch (candleInterval) {
            case CANDLE_INTERVAL_UNSPECIFIED -> CandleInterval.UNSPECIFIED;
            case CANDLE_INTERVAL_1_MIN -> CandleInterval.MIN_1;
            case CANDLE_INTERVAL_5_MIN -> CandleInterval.MIN_5;
            case CANDLE_INTERVAL_15_MIN -> CandleInterval.MIN_15;
            case CANDLE_INTERVAL_HOUR -> CandleInterval.HOUR;
            case CANDLE_INTERVAL_DAY -> CandleInterval.DAY;
            case CANDLE_INTERVAL_2_MIN -> CandleInterval.MIN_2;
            case CANDLE_INTERVAL_3_MIN -> CandleInterval.MIN_3;
            case CANDLE_INTERVAL_10_MIN -> CandleInterval.MIN_10;
            case CANDLE_INTERVAL_30_MIN -> CandleInterval.MIN_30;
            case CANDLE_INTERVAL_2_HOUR -> CandleInterval.HOUR_2;
            case CANDLE_INTERVAL_4_HOUR -> CandleInterval.HOUR_4;
            case CANDLE_INTERVAL_WEEK -> CandleInterval.WEEK;
            case CANDLE_INTERVAL_MONTH -> CandleInterval.MONTH;
            case UNRECOGNIZED -> null;
        };
    }
}
