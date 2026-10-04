package com.siberalt.singularity.presenter.google;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.presenter.google.render.DataRenderer;
import com.siberalt.singularity.presenter.google.render.FasterXmlRenderer;
import com.siberalt.singularity.presenter.google.series.BarAxis;
import com.siberalt.singularity.presenter.google.series.Column;
import com.siberalt.singularity.presenter.google.series.ColumnRole;
import com.siberalt.singularity.presenter.google.series.ColumnType;
import com.siberalt.singularity.presenter.google.series.SeriesChunk;
import com.siberalt.singularity.strategy.indicator.Macd;

import java.util.List;
import java.util.Map;

/**
 * MACD of the bars being drawn, written out beside the price for the page to load - the line, its signal
 * and the histogram, one row per bar of the price chart.
 * <p>
 * Like {@link RsiChart}, it is computed here by the same {@link Macd} the rest of the project reads, of
 * the bars of the axis rather than of the minutes behind them. Where there is no reading yet the row
 * carries {@code null}: a gap, not a zero.
 */
public class MacdChart {
    private static final String DEFAULT_OUTPUT = "src/main/resources/presenter/google/MacdChart.json";

    private int stepInterval = 1;

    private int fastPeriod = Macd.DEFAULT_FAST;

    private int slowPeriod = Macd.DEFAULT_SLOW;

    private int signalPeriod = Macd.DEFAULT_SIGNAL;

    private CandleInterval interval;

    private DataRenderer dataRenderer = new FasterXmlRenderer(DEFAULT_OUTPUT);

    public MacdChart(int stepInterval, DataRenderer dataRenderer) {
        this.stepInterval = stepInterval;
        this.dataRenderer = dataRenderer;
    }

    public MacdChart(int stepInterval) {
        this.stepInterval = stepInterval;
    }

    public MacdChart() {
    }

    /** The width of a bar, as for {@link PriceChart#setInterval}: the MACD is of those bars. */
    public MacdChart setInterval(CandleInterval interval) {
        this.interval = interval;

        return this;
    }

    public MacdChart setPeriods(int fastPeriod, int slowPeriod, int signalPeriod) {
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;

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
        Macd macd = new Macd(fastPeriod, slowPeriod, signalPeriod);
        Object[][] data = new Object[axis.rowsAt(stepInterval)][4];

        // Every bar goes through the indicator, drawn or not: skipping the ones a step leaves out would
        // change the averages.
        for (int row = 0; row < bars.size(); row++) {
            macd.add(bars.get(row));

            if (row % stepInterval == 0) {
                data[row / stepInterval] = new Object[]{
                    bars.get(row).getTime().toString(),
                    valueOf(macd.line()),
                    valueOf(macd.signalLine()),
                    valueOf(macd.histogram())
                };
            }
        }

        String name = "(" + fastPeriod + "," + slowPeriod + "," + signalPeriod + ")";

        return new SeriesChunk(
            List.of(
                new Column(ColumnType.DATE, ColumnRole.DOMAIN, "Time"),
                new Column(ColumnType.NUMBER, ColumnRole.DATA, "MACD" + name),
                new Column(ColumnType.NUMBER, ColumnRole.DATA, "Signal"),
                new Column(ColumnType.NUMBER, ColumnRole.DATA, "Histogram")
            ),
            data,
            List.of(
                Map.of(),
                Map.of("color", "#1a73e8", "lineWidth", 1),
                Map.of("color", "#e8710a", "lineWidth", 1),
                Map.of("type", "bars", "color", "#9aa0a6")
            )
        );
    }

    private static Double valueOf(double reading) {
        return Double.isNaN(reading) ? null : reading;
    }
}
