package com.siberalt.singularity.presenter.google;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.presenter.google.render.DataRenderer;
import com.siberalt.singularity.presenter.google.render.FasterXmlRenderer;
import com.siberalt.singularity.presenter.google.series.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VolumeChart {
    private int stepInterval = 30; // Default step interval for rendering
    private CandleInterval interval;
    private DataRenderer dataRenderer = new FasterXmlRenderer(
        "src/main/resources/presenter/google/VolumeChart.json"
    );
    private final List<SeriesProvider> seriesProviders = new ArrayList<>();

    public VolumeChart(int stepInterval, DataRenderer dataRenderer) {
        this.stepInterval = stepInterval;
        this.dataRenderer = dataRenderer;
    }

    public VolumeChart(int stepInterval) {
        this.stepInterval = stepInterval;
    }

    public VolumeChart() {
    }

    public VolumeChart addSeriesProvider(SeriesProvider seriesProvider) {
        this.seriesProviders.add(seriesProvider);
        return this;
    }

    /** The width of a bar, as for {@link PriceChart#setInterval}: volumes of a bar are summed. */
    public VolumeChart setInterval(CandleInterval interval) {
        this.interval = interval;
        return this;
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
        Map<String, Object> options = Map.of("type", "bars", "color", "#ff9824");

        var candleSeriesProvider = new CandleSeriesProvider(
            axis.bars(),
            options,
            "Volumes",
            candle -> (double) candle.volume()
        );

        SeriesDataAggregator aggregator = new SeriesDataAggregator().addSeriesProvider(candleSeriesProvider);
        seriesProviders.forEach(aggregator::addSeriesProvider);

        return aggregator.provide(axis, stepInterval).orElseThrow();
    }
}
