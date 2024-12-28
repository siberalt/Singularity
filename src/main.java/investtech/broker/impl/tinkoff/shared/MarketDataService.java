package investtech.broker.impl.tinkoff.shared;

import investtech.broker.common.ListTranslator;
import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.market.MarketDataServiceInterface;
import investtech.broker.contract.service.market.request.GetCandlesRequest;
import investtech.broker.contract.service.market.request.GetLastPricesRequest;
import investtech.broker.contract.service.market.response.GetCandlesResponse;
import investtech.broker.contract.service.market.response.GetLastPricesResponse;
import investtech.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import investtech.broker.impl.tinkoff.shared.translation.CandleIntervalTranslator;
import investtech.broker.impl.tinkoff.shared.translation.HistoricCandleTranslator;
import investtech.broker.impl.tinkoff.shared.translation.LastPriceTranslator;

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
    public GetLastPricesResponse getLastPrices(GetLastPricesRequest request) {
        var response = marketDataService.getLastPricesSync(request.getInstrumentsUid());

        return new GetLastPricesResponse()
                .setLastPrices(ListTranslator.translate(response, LastPriceTranslator::toContract));
    }
}
