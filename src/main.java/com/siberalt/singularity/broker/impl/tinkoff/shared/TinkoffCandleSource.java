package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.market.request.GetCandlesRequest;
import com.siberalt.singularity.broker.contract.service.market.response.HistoricCandle;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.entity.candle.CandleRangeMetadata;
import com.siberalt.singularity.entity.candle.MigrationCandleSource;
import com.siberalt.singularity.shared.TimePointRange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TinkoffCandleSource implements MigrationCandleSource {
    protected final MarketDataService marketDataService;
    protected final CandleInterval interval;
    protected final Map<String, CandleFactory> candleFactories = new ConcurrentHashMap<>();

    public TinkoffCandleSource(MarketDataService marketDataService, CandleInterval interval) {
        this.marketDataService = marketDataService;
        this.interval = interval;
    }

    @Override
    public List<Candle> getPeriod(String instrumentUid, Instant from, Instant to) {
        try {
            List<HistoricCandle> historicCandles = marketDataService.getCandles(
                GetCandlesRequest.of(from, to, interval, instrumentUid)
            ).getCandles();

            CandleFactory candleFactory = candleFactories.computeIfAbsent(instrumentUid, CandleFactory::new);
            List<Candle> candles = new ArrayList<>();
            for (HistoricCandle historicCandle : historicCandles) {
                candles.add(candleFactory.create(
                    historicCandle.getTime(),
                    historicCandle.getOpen(),
                    historicCandle.getClose(),
                    historicCandle.getHigh(),
                    historicCandle.getLow(),
                    historicCandle.getVolume(),
                    historicCandle.getVolumeBuy(),
                    historicCandle.getVolumeSell()
                ));
            }
            return candles;
        } catch (AbstractException e) {
            throw new RuntimeException("Ошибка при получении свечей из Tinkoff API", e);
        }
    }

    @Override
    public CandleRangeMetadata getRangeMetadata(String instrumentUid, Instant from, Instant to) {
        if (!from.isBefore(to)) {
            return CandleRangeMetadata.EMPTY;
        }
        return new CandleRangeMetadata(new TimePointRange(from, to), 1);
    }
}
