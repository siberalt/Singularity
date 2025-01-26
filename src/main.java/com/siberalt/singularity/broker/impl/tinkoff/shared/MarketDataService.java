package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.market.response.GetLastPricesResponse;
import com.siberalt.singularity.broker.common.ListTranslator;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.MarketDataServiceInterface;
import com.siberalt.singularity.broker.contract.service.market.request.GetCandlesRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetLastPricesRequest;
import com.siberalt.singularity.broker.contract.service.market.response.GetCandlesResponse;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.CandleIntervalTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.HistoricCandleTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.LastPriceTranslator;

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
