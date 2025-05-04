package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.market.response.GetTechAnalysisResponse;
import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.TechAnalysisServiceInterface;
import com.siberalt.singularity.broker.contract.service.market.request.GetTechAnalysisRequest;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.*;

public class TechAnalysisService implements TechAnalysisServiceInterface {
    protected ru.tinkoff.piapi.core.MarketDataService marketDataService;

    public TechAnalysisService(ru.tinkoff.piapi.core.MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Override
    public GetTechAnalysisResponse getTechAnalysis(GetTechAnalysisRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() -> marketDataService.getTechAnalysisSync(
                        ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.newBuilder()
                                .setIndicatorType(IndicatorTypeTranslator.toTinkoff(request.getIndicatorType()))
                                .setInstrumentUid(request.getInstrumentUid())
                                .setFrom(TimestampTranslator.toTinkoff(request.getFrom()))
                                .setTo(TimestampTranslator.toTinkoff(request.getTo()))
                                .setInterval(IndicatorIntervalTranslator.toTinkoff(request.getInterval()))
                                .setTypeOfPrice(TypeOfPriceTranslator.toTinkoff(request.getPriceType()))
                                .setLength(request.getLength())
                                .build()
                )
        );

        return new GetTechAnalysisResponse()
                .setTechnicalIndicators(
                        ListTranslator.translate(
                                response.getTechnicalIndicatorsList(),
                                TechAnalysisItemTranslator::toContract
                        )
                );
    }
}
