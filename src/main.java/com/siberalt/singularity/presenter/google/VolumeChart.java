package com.siberalt.singularity.presenter.google;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.presenter.google.render.DataRenderer;
import com.siberalt.singularity.presenter.google.render.FasterXmlRenderer;
import com.siberalt.singularity.presenter.google.series.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VolumeChart {
    private int stepInterval = 30; // Default step interval for rendering
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

    public void render(List<Candle> candles) {
        long start = candles.get(0).getIndex();
        long end = candles.get(candles.size() - 1).getIndex();

        Map<String, Object> options = Map.of("type", "bars", "color", "#ff9824");

        var candleSeriesProvider = new CandleSeriesProvider(
            candles,
            options,
            "Volumes",
            candle -> (double) candle.volume()
        );

        SeriesDataAggregator aggregator = new SeriesDataAggregator().addSeriesProvider(candleSeriesProvider);
        seriesProviders.forEach(aggregator::addSeriesProvider);

        SeriesChunk chunk = aggregator.provide(start, end, stepInterval).orElseThrow();

        dataRenderer.render(chunk);
    }
}
