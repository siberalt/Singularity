package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class CandleSeriesProvider implements SeriesProvider {
    private Function<Candle, Double> priceExtractor = Candle::getCloseAsDouble;
    private final List<Candle> candles;
    private String xAxisTitle = "Time";
    private String yAxisTitle = "Price";
    private Map<String, Object> xAxisOptions = Collections.emptyMap();
    private Map<String, Object> yAxisOptions;
    private String priceLineColor = "#4285F4"; // Default color
    private int priceLineWidth = 1; // Default point size

    public CandleSeriesProvider(List<Candle> candles) {
        this.candles = candles;
    }

    public CandleSeriesProvider(
        List<Candle> candles,
        Function<Candle, Double> priceExtractor,
        String xAxisTitle,
        String yAxisTitle,
        Map<String, Object> yAxisOptions,
        String priceLineColor,
        int priceLineWidth,
        Map<String, Object> xAxisOptions
    ) {
        this.candles = candles;
        this.priceExtractor = priceExtractor;
        this.xAxisTitle = xAxisTitle;
        this.yAxisTitle = yAxisTitle;
        this.yAxisOptions = yAxisOptions;
        this.priceLineColor = priceLineColor;
        this.priceLineWidth = priceLineWidth;
        this.xAxisOptions = xAxisOptions;
    }

    public CandleSeriesProvider(List<Candle> candles, Map<String, Object> yAxisOptions) {
        this.candles = candles;
        this.yAxisOptions = yAxisOptions;
    }

    public CandleSeriesProvider(
        List<Candle> candles,
        Map<String, Object> yAxisOptions,
        Function<Candle, Double> priceExtractor
    ) {
        this.candles = candles;
        this.priceExtractor = priceExtractor;
        this.yAxisOptions = yAxisOptions;
    }

    public CandleSeriesProvider(
        List<Candle> candles,
        Map<String, Object> yAxisOptions,
        String yAxisTitle,
        Function<Candle, Double> priceExtractor
    ) {
        this.candles = candles;
        this.priceExtractor = priceExtractor;
        this.yAxisOptions = yAxisOptions;
        this.yAxisTitle = yAxisTitle;
    }

    public CandleSeriesProvider(List<Candle> candles, Function<Candle, Double> priceExtractor) {
        this.candles = candles;
        this.priceExtractor = priceExtractor;
    }

    /**
     * Each candle in the row of the bar it falls in by time, every {@code stepInterval}-th bar
     * drawn. Normally the candles are the axis's own bars, one to a row; candles of another width
     * still land where they belong, the last of them winning a row they share.
     */
    @Override
    public Optional<SeriesChunk> provide(BarAxis axis, long stepInterval) {
        if (candles.isEmpty() || axis.isEmpty()) {
            return Optional.empty();
        }

        Object[][] data = new Object[axis.rowsAt(stepInterval)][2];
        boolean any = false;

        for (Candle candle : candles) {
            int row = axis.rowOfTime(candle.getTime());

            if (row < 0 || row >= axis.size() || row % stepInterval != 0) {
                continue;
            }

            data[(int) (row / stepInterval)] = new Object[]{candle.getTime().toString(), priceExtractor.apply(candle)};
            any = true;
        }

        return any ? Optional.of(new SeriesChunk(columns(), data, options())) : Optional.empty();
    }

    private List<Column> columns() {
        return List.of(
            new Column(ColumnType.DATE, ColumnRole.DOMAIN, xAxisTitle),
            new Column(ColumnType.NUMBER, ColumnRole.DATA, yAxisTitle)
        );
    }

    private List<Map<String, Object>> options() {
        return List.of(
            xAxisOptions,
            null == yAxisOptions
                ? Map.of("color", priceLineColor, "lineWidth", priceLineWidth)
                : yAxisOptions
        );
    }
}
