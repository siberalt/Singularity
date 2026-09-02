package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.market.request.GetCandlesRequest;
import com.siberalt.singularity.broker.contract.service.market.response.HistoricCandle;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleRangeMetadata;
import com.siberalt.singularity.entity.candle.MigrationCandleSource;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.shared.TimePointRange;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Индекс свечей здесь не проставляется (используется placeholder из
 * {@link TimePoint#TimePoint(Instant)}) - реальный time_index вычисляется
 * отдельным шагом после миграции ({@link com.siberalt.singularity.entity.candle.CandleIndexNormalizer}).
 * Благодаря этому источник не хранит никакого состояния между вызовами и
 * безопасен для параллельной обработки чанков одного инструмента.
 */
public class TinkoffCandleSource implements MigrationCandleSource {
    protected final MarketDataService marketDataService;
    protected final CandleInterval interval;

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

            List<Candle> candles = new ArrayList<>();
            for (HistoricCandle historicCandle : historicCandles) {
                candles.add(new Candle(
                    instrumentUid,
                    new TimePoint(historicCandle.getTime()),
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
        // Свечей из будущего не бывает - без этой обрезки CandleMigrationService
        // нарежет чанки вплоть до запрошенного to, часть из них уйдёт в API с
        // датой "из будущего", получит ошибку валидации и никогда не будет
        // учтена в прогрессе (чанк падает в catch и не помечается обработанным).
        Instant clampedTo = to.isAfter(Instant.now()) ? Instant.now() : to;

        if (!from.isBefore(clampedTo)) {
            return CandleRangeMetadata.EMPTY;
        }
        return new CandleRangeMetadata(new TimePointRange(from, clampedTo), 1);
    }
}
