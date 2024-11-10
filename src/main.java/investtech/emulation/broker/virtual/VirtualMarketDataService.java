package investtech.emulation.broker.virtual;

import investtech.broker.contract.service.market.MarketDataServiceInterface;
import investtech.broker.contract.service.market.request.CandleInterval;
import investtech.broker.contract.service.market.request.GetCandlesRequest;
import investtech.broker.contract.service.market.request.GetLastPricesRequest;
import investtech.broker.contract.service.market.response.GetCandlesResponse;
import investtech.broker.contract.service.market.response.GetLastPricesResponse;
import investtech.broker.contract.service.market.response.HistoricCandle;
import investtech.broker.contract.service.market.response.LastPrice;
import investtech.emulation.shared.market.candle.Candle;
import investtech.emulation.shared.market.candle.CandleStorageInterface;
import investtech.emulation.shared.market.candle.FindPriceParams;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class VirtualMarketDataService implements MarketDataServiceInterface {
    protected VirtualBroker virtualBroker;
    protected CandleStorageInterface candleStorage;

    public VirtualMarketDataService(VirtualBroker virtualBroker, CandleStorageInterface candleStorage) {
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
                .collect(Collectors.toList());

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
                    currentTime.minus(Duration.ofMinutes(30)),
                    currentTime
            );
            StreamSupport
                    .stream(candlesIterable.spliterator(), false)
                    .map(x -> LastPrice.of(instrumentUid, x.getTime(), x.getAveragePrice()))
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
            if (Objects.isNull(startIntervalCandle)) {
                startIntervalCandle = candle;
                candlesToUnite.add(candle.clone());
            } else if (startIntervalCandle.getTime().isAfter(candle.getTime())) {
                // TODO: throw exception
            } else if (candleInterval.belongsToInterval(startIntervalCandle, candle)) {
                candlesToUnite.add(candle);
            } else {
                startIntervalCandle = null;
                adaptedCandles.add(uniteCandles(candlesToUnite));
                candlesToUnite.clear();
            }
        }

        return adaptedCandles;
    }

    protected Candle uniteCandles(List<Candle> uniteCandles) {
        if (uniteCandles.isEmpty()) {
            throw new RuntimeException("No candles to unite. Empty list");
        }

        var unitedCandle = uniteCandles.stream().reduce(new Candle(), Candle::addCumulative);
        var candlesCount = uniteCandles.size();
        var firstCandle = uniteCandles.stream().findFirst().orElseThrow();

        return unitedCandle
                .setOpenPrice(unitedCandle.getOpenPrice().div(candlesCount))
                .setClosePrice(unitedCandle.getClosePrice().div(candlesCount))
                .setHighPrice(unitedCandle.getHighPrice().div(candlesCount))
                .setLowPrice(unitedCandle.getLowPrice().div(candlesCount))
                .setVolume(unitedCandle.getVolume() / candlesCount)
                .setInstrumentUid(firstCandle.getInstrumentUid())
                .setTime(firstCandle.getTime());
    }

    protected Candle getInstrumentLastCandle(String instrumentUid) {
        return candleStorage.getAt(instrumentUid, virtualBroker.context.getCurrentTime()).orElse(null);
    }
}
