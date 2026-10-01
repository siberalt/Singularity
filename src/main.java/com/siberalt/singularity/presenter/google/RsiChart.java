package com.siberalt.singularity.presenter.google;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.presenter.google.render.DataRenderer;
import com.siberalt.singularity.presenter.google.render.FasterXmlRenderer;
import com.siberalt.singularity.presenter.google.series.BarAxis;
import com.siberalt.singularity.presenter.google.series.CandleSeriesProvider;
import com.siberalt.singularity.presenter.google.series.SeriesChunk;
import com.siberalt.singularity.presenter.google.series.SeriesDataAggregator;
import com.siberalt.singularity.presenter.google.series.SeriesProvider;
import com.siberalt.singularity.strategy.indicator.IncrementalRsi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wilder's RSI of the bars being drawn, written out beside the price for the page to load.
 * <p>
 * The reading used to be computed in the browser, on the points the price chart happened to hold. That
 * was a second implementation of {@link IncrementalRsi} living in a language nobody measures anything in,
 * and it drifted from this one by definition - it could only ever be checked by eye. Here the same class
 * that the strategies and the studies read produces the series, so the line under the chart is the line
 * the rules trade on.
 * <p>
 * The RSI is of the <b>bars of the axis</b>, not of the minutes behind them: rolled up into hours, it is
 * the hourly RSI, which is the one the oversold rule is measured on. Before it has seen a period of
 * changes there is no reading, and the row carries {@code null} rather than fifty - a gap in the line is
 * honest about there being nothing to draw yet, where the middle of the scale would look like a market in
 * balance.
 */
public class RsiChart {
    private static final String DEFAULT_OUTPUT = "src/main/resources/presenter/google/RsiChart.json";

    private int stepInterval = 1;

    private int period = 14;

    private CandleInterval interval;

    private DataRenderer dataRenderer = new FasterXmlRenderer(DEFAULT_OUTPUT);

    private final List<SeriesProvider> seriesProviders = new ArrayList<>();

    public RsiChart(int stepInterval, DataRenderer dataRenderer) {
        this.stepInterval = stepInterval;
        this.dataRenderer = dataRenderer;
    }

    public RsiChart(int stepInterval) {
        this.stepInterval = stepInterval;
    }

    public RsiChart() {
    }

    public RsiChart addSeriesProvider(SeriesProvider seriesProvider) {
        this.seriesProviders.add(seriesProvider);

        return this;
    }

    /** The width of a bar, as for {@link PriceChart#setInterval}: the RSI is of those bars. */
    public RsiChart setInterval(CandleInterval interval) {
        this.interval = interval;

        return this;
    }

    public RsiChart setPeriod(int period) {
        this.period = period;

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
        List<Candle> bars = axis.bars();
        double[] readings = IncrementalRsi.seriesOf(bars, period);
        Map<Instant, Double> byTime = new HashMap<>(bars.size());

        for (int at = 0; at < bars.size(); at++) {
            byTime.put(bars.get(at).getTime(), Double.isNaN(readings[at]) ? null : readings[at]);
        }

        Map<String, Object> options = Map.of("color", "#6a1b9a", "lineWidth", 1);
        SeriesDataAggregator aggregator = new SeriesDataAggregator()
            .addSeriesProvider(new CandleSeriesProvider(bars, options, "RSI(" + period + ")",
                bar -> byTime.get(bar.getTime())));

        seriesProviders.forEach(aggregator::addSeriesProvider);

        return aggregator.provide(axis, stepInterval).orElseThrow();
    }
}
