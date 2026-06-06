package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.shared.RangeLong;

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

    @Override
    public Optional<SeriesChunk> provide(long start, long end, long stepInterval) {
        if (candles.isEmpty()) {
            return Optional.empty(); // No data to provide
        }

        // 1. Найти первую и последнюю свечу в диапазоне
        long firstIndex = candles.get(0).getIndex();
        long lastIndex = candles.get(candles.size() - 1).getIndex();

        if (end < firstIndex || start > lastIndex) {
            return Optional.empty();
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
            if (!RangeLong.belongsTo(start, end, candle.getIndex())) {
                continue;
            }

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
            xAxisOptions,
            null == yAxisOptions
                ? Map.of("color", priceLineColor, "lineWidth", priceLineWidth)
                : yAxisOptions
        );

        return Optional.of(new SeriesChunk(columns, data, options));
    }
}
