package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.market.request.GetCurrentPriceRequest;
import com.siberalt.singularity.broker.contract.service.market.response.GetCurrentPriceResponse;
import com.siberalt.singularity.broker.contract.service.market.response.GetLastPricesResponse;
import com.siberalt.singularity.broker.contract.service.market.response.LastPrice;
import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.request.GetCandlesRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetLastPricesRequest;
import com.siberalt.singularity.broker.contract.service.market.response.GetCandlesResponse;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.CandleIntervalTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.HistoricCandleTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.LastPriceTranslator;

import java.util.List;

public class MarketDataService implements com.siberalt.singularity.broker.contract.service.market.MarketDataService {
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
                .setPrices(ListTranslator.translate(response, LastPriceTranslator::toContract));
    }

    @Override
    public GetCurrentPriceResponse getCurrentPrice(GetCurrentPriceRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() ->
                marketDataService.getLastPricesSync(List.of(request.getInstrumentUid()))
        );

        LastPrice price = LastPriceTranslator.toContract(response.stream()
                .findFirst()
                .orElseThrow(() -> ExceptionBuilder.create(ErrorCode.INSTRUMENT_NOT_FOUND))
        );

        return new GetCurrentPriceResponse()
                .setPrice(price.getPrice())
                .setInstrumentUid(request.getInstrumentUid());
    }
}
