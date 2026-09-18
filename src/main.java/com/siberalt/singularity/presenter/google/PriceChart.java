package com.siberalt.singularity.presenter.google;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.presenter.google.render.DataRenderer;
import com.siberalt.singularity.presenter.google.render.FasterXmlRenderer;
import com.siberalt.singularity.presenter.google.series.BarAxis;
import com.siberalt.singularity.presenter.google.series.CandleSeriesProvider;
import com.siberalt.singularity.presenter.google.series.SeriesChunk;
import com.siberalt.singularity.presenter.google.series.SeriesDataAggregator;
import com.siberalt.singularity.presenter.google.series.SeriesProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A price line with whatever else is laid over it, one row per bar.
 * <p>
 * Given an interval, the chart rolls the candles it is handed up into bars of that width itself, and
 * everything added to it keeps speaking in the coordinates it was made in - extremes and levels in
 * the indices of the minute candles they were found on, orders in instants. The {@link BarAxis} puts
 * all of it in the row of the bar it belongs to. Without an interval the candles are drawn as the
 * bars they are.
 */
public class PriceChart {
    private long instrumentId;
    private ReadCandleRepository candleRepository;
    private Function<Candle, Double> priceExtractor = Candle::getCloseAsDouble;
    private int stepInterval = 30; // Default step interval for rendering
    private CandleInterval interval;
    private DataRenderer dataRenderer = new FasterXmlRenderer();
    private final List<SeriesProvider> seriesProviders = new ArrayList<>();

    public PriceChart(ReadCandleRepository candleRepository, long instrumentId) {
        this.candleRepository = candleRepository;
        this.instrumentId = instrumentId;
    }

    public PriceChart(
        ReadCandleRepository candleRepository,
        long instrumentId,
        Function<Candle, Double> priceExtractor
    ) {
        this.candleRepository = candleRepository;
        this.instrumentId = instrumentId;
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

    /**
     * The width of a bar: candles handed to the chart are rolled up to it before drawing. Null, the
     * default, draws them as they are. The step still applies on top - every step-th bar a row.
     */
    public PriceChart setInterval(CandleInterval interval) {
        this.interval = interval;
        return this;
    }

    public void render(Instant startTime, Instant endTime) {
        if (candleRepository == null) {
            throw new IllegalStateException("Candle repository is not set");
        }

        render(candleRepository.getPeriod(instrumentId, startTime, endTime));
    }

    public void render(List<Candle> candles) {
        dataRenderer.render(chunkOf(candles));
    }

    /** What {@link #render(List)} hands the renderer. */
    public SeriesChunk chunkOf(List<Candle> candles) {
        if (candles.isEmpty()) {
            throw new IllegalArgumentException("Nothing to draw: no candles");
        }

        BarAxis axis = interval == null ? BarAxis.ofBars(candles) : BarAxis.of(candles, interval);

        SeriesDataAggregator aggregator = new SeriesDataAggregator()
            .addSeriesProvider(new CandleSeriesProvider(axis.bars(), priceExtractor));
        seriesProviders.forEach(aggregator::addSeriesProvider);

        return aggregator.provide(axis, stepInterval).orElseThrow();
    }

    public static long adjustToStepInterval(long value, long stepInterval) {
        long remainder = value % stepInterval;
        return remainder <= (stepInterval / 2) ? value - remainder : value - remainder + stepInterval;
    }
}
