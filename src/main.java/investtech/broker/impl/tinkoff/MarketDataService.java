package investtech.broker.impl.tinkoff;

import investtech.broker.common.ListTranslator;
import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.market.MarketDataServiceInterface;
import investtech.broker.contract.service.market.request.GetCandlesRequest;
import investtech.broker.contract.service.market.request.GetLastPricesRequest;
import investtech.broker.contract.service.market.request.GetTechAnalysisRequest;
import investtech.broker.contract.service.market.response.GetCandlesResponse;
import investtech.broker.contract.service.market.response.GetLastPricesResponse;
import investtech.broker.contract.service.market.response.GetTechAnalysisResponse;
import investtech.broker.impl.tinkoff.exception.ExceptionConverter;
import investtech.broker.impl.tinkoff.translation.*;

public class MarketDataService implements MarketDataServiceInterface {
    protected ru.tinkoff.piapi.core.MarketDataService marketDataService;

    public MarketDataService(ru.tinkoff.piapi.core.MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Override
    public GetCandlesResponse getCandles(GetCandlesRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() ->
                marketDataService.getCandlesSync(
                        request.getInstrumentUid(),
                        request.getFrom(),
                        request.getTo(),
                        CandleIntervalTranslator.toTinkoff(request.getInterval())
                )
        );

        return new GetCandlesResponse()
                .setCandles(ListTranslator.translate(response, HistoricCandleTranslator::toContract));
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

    @Override
    public GetLastPricesResponse getLastPrices(GetLastPricesRequest request) {
        var response = marketDataService.getLastPricesSync(request.getInstrumentsUid());

        return new GetLastPricesResponse()
                .setLastPrices(ListTranslator.translate(response, LastPriceTranslator::toContract));
    }
}
