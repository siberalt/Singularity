package investtech.broker.impl.mock.simulation;

import investtech.broker.contract.service.market.MarketDataServiceInterface;
import investtech.broker.contract.service.market.request.CandleInterval;
import investtech.broker.contract.service.market.request.GetCandlesRequest;
import investtech.broker.contract.service.market.request.GetLastPricesRequest;
import investtech.broker.contract.service.market.response.GetCandlesResponse;
import investtech.broker.contract.service.market.response.GetLastPricesResponse;
import investtech.broker.contract.service.market.response.HistoricCandle;
import investtech.broker.contract.service.market.response.LastPrice;
import investtech.broker.contract.value.quotation.Quotation;
import investtech.simulation.shared.market.candle.Candle;
import investtech.simulation.shared.market.candle.CandleStorageInterface;
import investtech.simulation.shared.market.candle.FindPriceParams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

public class MockMarketDataService implements MarketDataServiceInterface {
    protected MockBroker virtualBroker;
    protected CandleStorageInterface candleStorage;

    public MockMarketDataService(MockBroker virtualBroker, CandleStorageInterface candleStorage) {
        this.virtualBroker = virtualBroker;
        this.candleStorage = candleStorage;
    }

    @Override
    public GetCandlesResponse getCandles(GetCandlesRequest request) {
        var iterableCandles = candleStorage.getPeriod(
                request.getInstrumentUid(),
                request.getFrom(),
                request.getTo()
        );

        var candles = adaptCandlesForInterval(iterableCandles, request.getInterval())
                .stream()
                .map(HistoricCandle::of)
                .toList();

        return new GetCandlesResponse()
                .setCandles(candles);
    }

    @Override
    public GetLastPricesResponse getLastPrices(GetLastPricesRequest request) {
        var currentTime = virtualBroker.context.getCurrentTime();

        List<LastPrice> lastPrices = new ArrayList<>();

        for (var instrumentUid : request.getInstrumentsUid()) {
            var candlesIterable = candleStorage.getPeriod(
                    instrumentUid,
                    currentTime.minus(request.getPeriod()),
                    currentTime
            );
            StreamSupport
                    .stream(candlesIterable.spliterator(), false)
                    .map(x -> LastPrice.of(instrumentUid, x.getTime(), x.getOpenPrice()))
                    .forEach(lastPrices::add);
        }

        return new GetLastPricesResponse().setLastPrices(lastPrices);
    }

    protected Optional<Candle> getCandleAt(String instrumentUid, Instant at) {
        return candleStorage.getAt(instrumentUid, at);
    }

    protected List<Candle> findCandlesByClosePrice(CandleInterval interval, FindPriceParams findParams) {
        return adaptCandlesForInterval(this.candleStorage.findByOpenPrice(findParams), interval);
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

    protected Candle getInstrumentLastCandle(String instrumentUid) {
        return candleStorage.getAt(instrumentUid, virtualBroker.context.getCurrentTime()).orElse(null);
    }
}
