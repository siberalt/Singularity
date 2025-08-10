package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.market.request.GetCandlesRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetCurrentPriceRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetLastPricesRequest;
import com.siberalt.singularity.broker.contract.service.market.response.*;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import com.siberalt.singularity.entity.candle.Candle;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MockMarketDataService implements MarketDataService {
    protected MockBroker virtualBroker;
    protected ReadCandleRepository candleRepository;

    public MockMarketDataService(MockBroker virtualBroker, ReadCandleRepository candleStorage) {
        this.virtualBroker = virtualBroker;
        this.candleRepository = candleStorage;
    }

    @Override
    public GetCandlesResponse getCandles(GetCandlesRequest request) {
        Iterable<Candle> iterableCandles = candleRepository.getPeriod(
                request.getInstrumentUid(),
                request.getFrom(),
                request.getTo()
        );

        List<HistoricCandle> candles = adaptCandlesForInterval(iterableCandles, request.getInterval())
                .stream()
                .map(HistoricCandle::of)
                .toList();

        return new GetCandlesResponse()
                .setCandles(candles);
    }

    @Override
    public GetLastPricesResponse getLastPrices(GetLastPricesRequest request) {
        Instant currentTime = virtualBroker.clock.currentTime();

        List<LastPrice> lastPrices = new ArrayList<>();

        if (null == request.getPeriod()) {
            request.setPeriod(Duration.ofMinutes(30));
        }

        for (String instrumentUid : request.getInstrumentsUid()) {
            List<Candle> candles = candleRepository.getPeriod(
                    instrumentUid,
                    currentTime.minus(request.getPeriod()),
                    currentTime
            );
            candles.stream()
                    .map(x -> LastPrice.of(instrumentUid, x.getTime(), x.getOpenPrice()))
                    .forEach(lastPrices::add);
        }

        return new GetLastPricesResponse().setPrices(lastPrices);
    }

    @Override
    public GetCurrentPriceResponse getCurrentPrice(GetCurrentPriceRequest request) throws AbstractException {
        String instrumentUid = request.getInstrumentUid();
        Instant at = virtualBroker.clock.currentTime();

        Optional<Candle> candleOpt = candleRepository.findClosestBefore(instrumentUid, at);

        if (candleOpt.isEmpty()) {
            throw ExceptionBuilder.create(ErrorCode.INSTRUMENT_NOT_FOUND);
        }

        Candle candle = candleOpt.get();
        Quotation price = candle.getOpenPrice();

        return new GetCurrentPriceResponse()
            .setInstrumentUid(instrumentUid)
            .setPrice(price);
    }

    protected Optional<Candle> findClosestBefore(String instrumentUid, Instant at) {
        return candleRepository.findClosestBefore(instrumentUid, at);
    }

    protected List<Candle> findCandlesByOpenPrice(CandleInterval interval, FindPriceParams findParams) {
        return adaptCandlesForInterval(this.candleRepository.findByOpenPrice(findParams), interval);
    }

    protected List<Candle> adaptCandlesForInterval(Iterable<Candle> candles, CandleInterval candleInterval) {
        List<Candle> adaptedCandles = new ArrayList<>(), candlesToUnite = new ArrayList<>();
        Candle startIntervalCandle = null;

        for (var candle : candles) {
            if (startIntervalCandle == null) {
                startIntervalCandle = candle;
                candlesToUnite.add(candle.clone());
            } else if (startIntervalCandle.getTime().isAfter(candle.getTime())) {
                throw new RuntimeException("Candles are not sorted");
            } else if (candleInterval.belongsToInterval(startIntervalCandle, candle)) {
                candlesToUnite.add(candle.clone());
            } else {
                adaptedCandles.add(uniteCandles(candlesToUnite));
                candlesToUnite.clear();
                startIntervalCandle = candle;
                candlesToUnite.add(candle.clone());
            }
        }

        if (!candlesToUnite.isEmpty()) {
            adaptedCandles.add(uniteCandles(candlesToUnite));
        }

        return adaptedCandles;
    }

    protected Candle uniteCandles(List<Candle> uniteCandles) {
        if (uniteCandles.isEmpty()) {
            throw new RuntimeException("No candles to unite. Empty list");
        }

        var candlesCount = uniteCandles.size();
        Quotation openAvg = Quotation.of(BigDecimal.ZERO);
        Quotation closeAvg = Quotation.of(BigDecimal.ZERO);
        Quotation highAvg = Quotation.of(BigDecimal.ZERO);
        Quotation lowAvg = Quotation.of(BigDecimal.ZERO);
        long volumeAvg = 0;

        for (var uniteCandle : uniteCandles) {
            openAvg = openAvg.add(uniteCandle.getOpenPrice());
            closeAvg = closeAvg.add(uniteCandle.getClosePrice());
            highAvg = highAvg.add(uniteCandle.getHighPrice());
            lowAvg = lowAvg.add(uniteCandle.getLowPrice());
            volumeAvg += uniteCandle.getVolume();
        }

        openAvg = openAvg.divide(candlesCount);
        closeAvg = closeAvg.divide(candlesCount);
        highAvg = highAvg.divide(candlesCount);
        lowAvg = lowAvg.divide(candlesCount);
        volumeAvg /= candlesCount;
        var firstCandle = uniteCandles.stream().findFirst().orElseThrow();

        return new Candle()
                .setOpenPrice(openAvg)
                .setClosePrice(closeAvg)
                .setHighPrice(highAvg)
                .setLowPrice(lowAvg)
                .setVolume(volumeAvg)
                .setInstrumentUid(firstCandle.getInstrumentUid())
                .setTime(firstCandle.getTime());
    }

    protected Candle getInstrumentCurrentCandle(String instrumentUid) {
        return candleRepository.findClosestBefore(instrumentUid, virtualBroker.clock.currentTime()).orElse(null);
    }
}
