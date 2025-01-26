package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.market.request.IndicatorType;

public class IndicatorTypeTranslator {

    public static ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorType toTinkoff(IndicatorType indicatorType) {
        return switch (indicatorType) {
            case UNSPECIFIED ->
                    ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorType.INDICATOR_TYPE_UNSPECIFIED;
            case BB -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorType.INDICATOR_TYPE_BB;
            case EMA -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorType.INDICATOR_TYPE_EMA;
            case RSI -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorType.INDICATOR_TYPE_RSI;
            case MACD -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorType.INDICATOR_TYPE_MACD;
            case SMA -> ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorType.INDICATOR_TYPE_SMA;
        };
    }

    public static IndicatorType toContract(ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.IndicatorType indicatorType) {
        return switch (indicatorType) {
            case INDICATOR_TYPE_UNSPECIFIED -> IndicatorType.UNSPECIFIED;
            case INDICATOR_TYPE_BB -> IndicatorType.BB;
            case INDICATOR_TYPE_EMA -> IndicatorType.EMA;
            case INDICATOR_TYPE_RSI -> IndicatorType.RSI;
            case INDICATOR_TYPE_MACD -> IndicatorType.MACD;
            case INDICATOR_TYPE_SMA -> IndicatorType.SMA;
            case UNRECOGNIZED -> null;
        };
    }
}
