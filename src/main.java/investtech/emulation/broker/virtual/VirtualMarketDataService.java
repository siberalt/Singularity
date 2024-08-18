package investtech.emulation.broker.virtual;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.exception.UnimplementedException;
import investtech.broker.contract.service.market.MarketDataServiceInterface;
import investtech.broker.contract.service.market.request.CandleInterval;
import investtech.broker.contract.service.market.request.GetCandlesRequest;
import investtech.broker.contract.service.market.request.GetLastPricesRequest;
import investtech.broker.contract.service.market.request.GetTechAnalysisRequest;
import investtech.broker.contract.service.market.response.GetCandlesResponse;
import investtech.broker.contract.service.market.response.GetLastPricesResponse;
import investtech.broker.contract.service.market.response.GetTechAnalysisResponse;
import investtech.broker.contract.service.market.response.LastPrice;
import investtech.broker.contract.value.quatation.Quotation;
import investtech.emulation.shared.market.candle.Candle;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;

public class VirtualMarketDataService implements MarketDataServiceInterface {
    protected VirtualBroker virtualBroker;

    public VirtualMarketDataService(VirtualBroker virtualBroker) {
        this.virtualBroker = virtualBroker;
    }

    @Override
    public GetCandlesResponse getCandles(GetCandlesRequest request) throws AbstractException {
        var iterableCandles = virtualBroker.candleStorage.getPeriod(
                request.getInstrumentUid(),
                request.getFrom(),
                request.getTo()
        );

        for (var candle : iterableCandles) {
        }

        return null;
    }

    @Override
    public GetTechAnalysisResponse getTechAnalysis(GetTechAnalysisRequest request) throws AbstractException {
        throw new UnimplementedException("Method getTechAnalysis is not implemented");
    }

    @Override
    public GetLastPricesResponse getLastPrices(GetLastPricesRequest request) throws AbstractException {
        var currentTime = virtualBroker.context.getCurrentTime();

        List<LastPrice> lastPrices = new ArrayList<>();

        for (var instrumentUid : request.getInstrumentsUid()) {
            var candlesIterable = virtualBroker.candleStorage.getPeriod(
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

    protected List<Candle> adaptCandlesForInterval(Iterable<Candle> candles, CandleInterval candleInterval) {
        List<Candle> adaptedCandles = new ArrayList<>();
        Candle startIntervalCandle = null;


        for (var candle : candles) {
            if (Objects.isNull(startIntervalCandle)) {
                startIntervalCandle = candle;
            } else if (candleInterval.belongsToInterval(startIntervalCandle, candle)) {

            }
        }
    }

    protected boolean belongsToInterval(Candle startCandle, Candle currentCandle, CandleInterval candleInterval) {
        return switch (candleInterval) {
            case MIN_1 -> ChronoUnit.MINUTES.between(startCandle.getTime(), currentCandle.getTime()) <= 1;
            case MIN_2 -> ChronoUnit.MINUTES.between(startCandle.getTime(), currentCandle.getTime()) <= 2;
            case MIN_3 -> ChronoUnit.MINUTES.between(startCandle.getTime(), currentCandle.getTime()) <= 3;
            case MIN_1 -> ChronoUnit.MINUTES.between(startCandle.getTime(), currentCandle.getTime()) <= 1;
        };
    }

    protected Quotation getInstrumentCurrentPrice(String instrumentUid) {
        var candleOptional = virtualBroker.candleStorage.getAt(instrumentUid, virtualBroker.context.getCurrentTime());

        return candleOptional.map(Candle::getAveragePrice).orElse(null);
    }
}
