package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.service.market.request.IndicatorInterval;

public class IndicatorIntervalTranslator {
    public static ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval toTinkoff(IndicatorInterval indicatorInterval) {
        return switch (indicatorInterval) {
            case UNSPECIFIED ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_UNSPECIFIED;
            case ONE_MINUTE ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_ONE_MINUTE;
            case FIVE_MINUTES ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_FIVE_MINUTES;
            case FIFTEEN_MINUTES ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_FIFTEEN_MINUTES;
            case ONE_HOUR ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_ONE_HOUR;
            case ONE_DAY ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_ONE_DAY;
            case MIN_2 ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_2_MIN;
            case MIN_3 ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_3_MIN;
            case MIN_10 ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_10_MIN;
            case MIN_30 ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_30_MIN;
            case HOUR_2 ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_2_HOUR;
            case HOUR_4 ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_4_HOUR;
            case WEEK -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_WEEK;
            case MONTH ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_MONTH;
        };
    }

    public static IndicatorInterval toContract(ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorInterval indicatorInterval) {
        return switch (indicatorInterval) {
            case INDICATOR_INTERVAL_UNSPECIFIED -> IndicatorInterval.UNSPECIFIED;
            case INDICATOR_INTERVAL_ONE_MINUTE -> IndicatorInterval.ONE_MINUTE;
            case INDICATOR_INTERVAL_FIVE_MINUTES -> IndicatorInterval.FIVE_MINUTES;
            case INDICATOR_INTERVAL_FIFTEEN_MINUTES -> IndicatorInterval.FIFTEEN_MINUTES;
            case INDICATOR_INTERVAL_ONE_HOUR -> IndicatorInterval.ONE_HOUR;
            case INDICATOR_INTERVAL_ONE_DAY -> IndicatorInterval.ONE_DAY;
            case INDICATOR_INTERVAL_2_MIN -> IndicatorInterval.MIN_2;
            case INDICATOR_INTERVAL_3_MIN -> IndicatorInterval.MIN_3;
            case INDICATOR_INTERVAL_10_MIN -> IndicatorInterval.MIN_10;
            case INDICATOR_INTERVAL_30_MIN -> IndicatorInterval.MIN_30;
            case INDICATOR_INTERVAL_2_HOUR -> IndicatorInterval.HOUR_2;
            case INDICATOR_INTERVAL_4_HOUR -> IndicatorInterval.HOUR_4;
            case INDICATOR_INTERVAL_WEEK -> IndicatorInterval.WEEK;
            case INDICATOR_INTERVAL_MONTH -> IndicatorInterval.MONTH;
            case UNRECOGNIZED -> null;
        };
    }
}
