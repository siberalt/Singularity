package com.siberalt.singularity.presenter.google;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.presenter.google.render.DataRenderer;
import com.siberalt.singularity.presenter.google.render.FasterXmlRenderer;
import com.siberalt.singularity.presenter.google.series.CandlePriceSeriesProvider;
import com.siberalt.singularity.presenter.google.series.SeriesChunk;
import com.siberalt.singularity.presenter.google.series.SeriesDataAggregator;
import com.siberalt.singularity.presenter.google.series.SeriesProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class PriceChart {
    private String instrumentUid;
    private ReadCandleRepository candleRepository;
    private Function<Candle, Double> priceExtractor = c -> c.getClosePrice().toBigDecimal().doubleValue();
    private int stepInterval = 30; // Default step interval for rendering
    private DataRenderer dataRenderer = new FasterXmlRenderer();
    private final List<SeriesProvider> seriesProviders = new ArrayList<>();

    public PriceChart(ReadCandleRepository candleRepository, String instrumentUid) {
        this.candleRepository = candleRepository;
        this.instrumentUid = instrumentUid;
    }

    public PriceChart(
        ReadCandleRepository candleRepository,
        String instrumentUid,
        Function<Candle, Double> priceExtractor
    ) {
        this.candleRepository = candleRepository;
        this.instrumentUid = instrumentUid;
        this.priceExtractor = priceExtractor;
    }

    public PriceChart(Function<Candle, Double> priceExtractor) {
        this.priceExtractor = priceExtractor;
    }

    public PriceChart setDataRenderer(DataRenderer dataRenderer) {
        this.dataRenderer = dataRenderer;
        return this;
    }

    public PriceChart addSeriesProvider(SeriesProvider seriesProvider) {
        this.seriesProviders.add(seriesProvider);
        return this;
    }

    public PriceChart setStepInterval(int stepInterval) {
        this.stepInterval = stepInterval;
        return this;
    }

    public void render(Instant startTime, Instant endTime) {
        if (candleRepository == null) {
            throw new IllegalStateException("Candle repository is not set");
        }

        List<Candle> candles = candleRepository.getPeriod(instrumentUid, startTime, endTime);

        long start = 0, end = candles.size() - 1;

        SeriesDataAggregator aggregator = new SeriesDataAggregator()
            .addSeriesProvider(new CandlePriceSeriesProvider(candles).setPriceExtractor(priceExtractor));
        seriesProviders.forEach(aggregator::addSeriesProvider);

        SeriesChunk chunk = aggregator.provide(start, end, stepInterval).orElseThrow();

        dataRenderer.render(chunk);
    }

    public void render(List<Candle> candles) {
        long start = 0, end = candles.size() - 1;

        SeriesDataAggregator aggregator = new SeriesDataAggregator()
            .addSeriesProvider(new CandlePriceSeriesProvider(candles).setPriceExtractor(priceExtractor));
        seriesProviders.forEach(aggregator::addSeriesProvider);

        SeriesChunk chunk = aggregator.provide(start, end, stepInterval).orElseThrow();

        dataRenderer.render(chunk);
    }

    public static long adjustToStepInterval(long value, long stepInterval) {
        long remainder = value % stepInterval;
        return remainder <= (stepInterval / 2) ? value - remainder : value - remainder + stepInterval;
    }
}
