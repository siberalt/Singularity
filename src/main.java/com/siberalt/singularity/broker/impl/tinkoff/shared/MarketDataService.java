package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.market.request.GetCurrentPriceRequest;
import com.siberalt.singularity.broker.contract.service.market.response.GetCurrentPriceResponse;
import com.siberalt.singularity.broker.contract.service.market.response.GetLastPricesResponse;
import com.siberalt.singularity.broker.contract.service.market.response.LastPrice;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.TimestampTranslator;
import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.request.GetCandlesRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetLastPricesRequest;
import com.siberalt.singularity.broker.contract.service.market.response.GetCandlesResponse;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.CandleIntervalTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.HistoricCandleTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.LastPriceTranslator;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;

public class MarketDataService implements com.siberalt.singularity.broker.contract.service.market.MarketDataService {
    protected MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataService;

    public MarketDataService(MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataService) {
        this.marketDataService = marketDataService;
    }

    @Override
    public GetCandlesResponse getCandles(GetCandlesRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() ->
                marketDataService.getCandles(
                    ru.tinkoff.piapi.contract.v1.GetCandlesRequest.newBuilder()
                        .setInstrumentId(request.getInstrumentUid())
                        .setFrom(TimestampTranslator.toTinkoff(request.getFrom()))
                        .setTo(TimestampTranslator.toTinkoff(request.getTo()))
                        .setInterval(CandleIntervalTranslator.toTinkoff(request.getInterval()))
                        .build()
                )
        );

        return new GetCandlesResponse()
                .setCandles(ListTranslator.translate(response.getCandlesList(), HistoricCandleTranslator::toContract));
    }

    @Override
    public GetLastPricesResponse getLastPrices(GetLastPricesRequest request) {
        ru.tinkoff.piapi.contract.v1.GetLastPricesRequest.Builder getLastPricesRequestBuilder = ru.tinkoff.piapi.contract.v1.GetLastPricesRequest.newBuilder();

        request.getInstrumentsUid().forEach(getLastPricesRequestBuilder::addInstrumentId);

        var response = marketDataService.getLastPrices(getLastPricesRequestBuilder.build());

        return new GetLastPricesResponse()
                .setPrices(ListTranslator.translate(response.getLastPricesList(), LastPriceTranslator::toContract));
    }

    @Override
    public GetCurrentPriceResponse getCurrentPrice(GetCurrentPriceRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() ->
            marketDataService.getLastPrices(
                ru.tinkoff.piapi.contract.v1.GetLastPricesRequest.newBuilder()
                    .addInstrumentId(request.getInstrumentUid())
                    .build()
            )
        );

        LastPrice price = LastPriceTranslator.toContract(response.getLastPricesList()
                .stream()
                .findFirst()
                .orElseThrow(() -> ExceptionBuilder.create(ErrorCode.INSTRUMENT_NOT_FOUND))
        );

        return new GetCurrentPriceResponse()
                .setPrice(price.getPrice())
                .setInstrumentUid(request.getInstrumentUid());
    }
}
