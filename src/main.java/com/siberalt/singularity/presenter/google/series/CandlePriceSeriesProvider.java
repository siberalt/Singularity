package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class CandlePriceSeriesProvider implements SeriesProvider {
    private Function<Candle, Double> priceExtractor = c -> c.getClosePrice().toDouble();
    private final List<Candle> candles;
    private String xAxisTitle = "Time";
    private String yAxisTitle = "Price";
    private String priceLineColor = "#4285F4"; // Default color
    private int priceLineWidth = 1; // Default point size

    public CandlePriceSeriesProvider(List<Candle> candles) {
        this.candles = candles;
    }

    public CandlePriceSeriesProvider setPriceLineColor(String priceLineColor) {
        this.priceLineColor = priceLineColor;
        return this;
    }

    public CandlePriceSeriesProvider setPriceLineWidth(int priceLineWidth) {
        this.priceLineWidth = priceLineWidth;
        return this;
    }

    public CandlePriceSeriesProvider setPriceExtractor(Function<Candle, Double> priceExtractor) {
        this.priceExtractor = priceExtractor;
        return this;
    }

    public CandlePriceSeriesProvider setxAxisTitle(String xAxisTitle) {
        this.xAxisTitle = xAxisTitle;
        return this;
    }

    public CandlePriceSeriesProvider setyAxisTitle(String yAxisTitle) {
        this.yAxisTitle = yAxisTitle;
        return this;
    }

    @Override
    public Optional<SeriesChunk> provide(long start, long end, long stepInterval) {
        if (candles.isEmpty()) {
            return Optional.empty(); // No data to provide
        }

        List<Column> columns = List.of(
            new Column(ColumnType.DATE, ColumnRole.DOMAIN, xAxisTitle),
            new Column(ColumnType.NUMBER, ColumnRole.DATA, yAxisTitle)
        );

        long index = 0;
        Object[][] data = new Object[(int) ((end - start) / stepInterval + 1)][columns.size()];

        if (data.length == 0) {
            return Optional.empty(); // No data to provide
        }

        for (Candle candle : candles) {
            int intervalIndex = (int) (index / stepInterval);

            if (intervalIndex >= data.length) {
                break; // Prevents ArrayIndexOutOfBoundsException
            }

            if (index % stepInterval == 0) {
                data[intervalIndex] = new Object[]{
                    candle.getTime().toString(),
                    priceExtractor.apply(candle)
                };
            }

            index++;
        }

        List<Map<String, Object>> options = List.of(
            Collections.emptyMap(), // No additional options for this series
            Map.of(
                "color", priceLineColor,
                "lineWidth", priceLineWidth
            )
        );

        return Optional.of(new SeriesChunk(columns, data, options));
    }
}
