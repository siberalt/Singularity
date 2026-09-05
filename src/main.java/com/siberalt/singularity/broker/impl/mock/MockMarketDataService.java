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
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.strategy.context.Clock;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MockMarketDataService implements MarketDataService, SimulationMarketData {
    protected Clock clock;
    protected ReadCandleRepository candleRepository;

    public MockMarketDataService(Clock clock, ReadCandleRepository candleStorage) {
        this.clock = clock;
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
        Instant currentTime = clock.currentTime();

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
                    .map(x -> LastPrice.of(instrumentUid, x.getTime(), x.open()))
                    .forEach(lastPrices::add);
        }

        return new GetLastPricesResponse().setPrices(lastPrices);
    }

    @Override
    public GetCurrentPriceResponse getCurrentPrice(GetCurrentPriceRequest request) throws AbstractException {
        String instrumentUid = request.getInstrumentUid();
        Instant at = clock.currentTime();

        List<Candle> candleList = candleRepository.findBeforeOrEqual(instrumentUid, at, 1);

        if (candleList.isEmpty()) {
            throw ExceptionBuilder.create(ErrorCode.INSTRUMENT_NOT_FOUND);
        }

        Candle candle = candleList.getFirst();
        Quotation price = candle.open();

        return new GetCurrentPriceResponse()
            .setInstrumentUid(instrumentUid)
            .setPrice(price);
    }

    @Override
    public Optional<Candle> lastCandleAtOrBefore(String instrumentUid, Instant at) {
        List<Candle> candles = candleRepository.findBeforeOrEqual(instrumentUid, at, 1);
        return candles.isEmpty() ? Optional.empty() : Optional.ofNullable(candles.getFirst());
    }

    @Override
    public Optional<Candle> nextCandleAtOrAfter(String instrumentUid, Instant at) {
        List<Candle> candles = candleRepository.findAfterOrEqual(instrumentUid, at, 1);
        return candles.isEmpty() ? Optional.empty() : Optional.ofNullable(candles.getFirst());
    }

    @Override
    public List<Candle> findByPrice(CandleInterval interval, FindPriceParams findParams) {
        return adaptCandlesForInterval(this.candleRepository.findByPrice(findParams), interval);
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

    /**
     * Aggregates a run of candles into the single candle of a wider interval: open is taken from
     * the first candle, close from the last one, high/low are the extremes over the run and the
     * volumes are summed. Averaging any of these would distort the resulting bar - notably the
     * volume, which is a count and only ever adds up.
     */
    protected Candle uniteCandles(List<Candle> uniteCandles) {
        if (uniteCandles.isEmpty()) {
            throw new RuntimeException("No candles to unite. Empty list");
        }

        Candle firstCandle = uniteCandles.getFirst();
        Candle lastCandle = uniteCandles.getLast();
        Quotation high = firstCandle.high();
        Quotation low = firstCandle.low();
        long volume = 0;
        long volumeBuy = 0;
        long volumeSell = 0;

        for (var uniteCandle : uniteCandles) {
            if (uniteCandle.high().isGreaterThan(high)) {
                high = uniteCandle.high();
            }

            if (uniteCandle.low().isLessThan(low)) {
                low = uniteCandle.low();
            }

            volume += uniteCandle.volume();
            volumeBuy += uniteCandle.volumeBuy();
            volumeSell += uniteCandle.volumeSell();
        }

        return new Candle(
            firstCandle.instrumentUid(),
            firstCandle.timePoint(),
            firstCandle.open(),
            lastCandle.close(),
            high,
            low,
            volume,
            volumeBuy,
            volumeSell
        );
    }

    @Override
    public Candle currentCandle(String instrumentUid) {
        List<Candle> candles = candleRepository.findBeforeOrEqual(
            instrumentUid,
            clock.currentTime(),
            1
        );
        return candles.isEmpty() ? null : candles.getFirst();
    }
}
