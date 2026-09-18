package com.siberalt.singularity.presenter.google;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationType;
import com.siberalt.singularity.presenter.google.series.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceChartTest {
    private static final Instant OPEN = Instant.parse("2025-03-03T07:00:00Z");

    /**
     * A price line, the orders on it and a level over it all have to share rows. Rolled up into
     * hours, a candle's index, its position and its row stop being the same number, and each layer
     * used to land somewhere of its own.
     */
    @Test
    void placesEveryLayerOnTheHourItBelongsTo() {
        // Three uneven hours: 60 minutes, 45 minutes, 60 minutes, indices running on without gaps.
        List<Candle> minutes = new ArrayList<>();
        long index = 1000;
        addMinutes(minutes, OPEN, 60, index, 100);
        addMinutes(minutes, OPEN.plusSeconds(3600), 45, index + 60, 200);
        addMinutes(minutes, OPEN.plusSeconds(7200), 60, index + 105, 300);

        PointSeriesProvider extremes = new PointSeriesProvider("Extremes");
        extremes.addPoint(index + 104, 7.0); // the last minute of the second hour

        OrderSeriesProvider orders = new OrderSeriesProvider(List.of(
            order(OPEN.plusSeconds(3600 + 50 * 60), OperationType.BUY, 5.0),  // 08:50, a quiet minute of the second hour
            order(OPEN.plusSeconds(7200 + 59 * 60), OperationType.SELL, 6.0)  // 09:59
        ), minutes);

        FunctionGroupSeriesProvider levels = new FunctionGroupSeriesProvider("Level");
        levels.addFunction(index + 70, index + 200, x -> x); // from inside the second hour into the third

        SeriesChunk chunk = new PriceChart(Candle::getCloseAsDouble)
            .setInterval(CandleInterval.HOUR)
            .setStepInterval(1)
            .addSeriesProvider(extremes)
            .addSeriesProvider(orders)
            .addSeriesProvider(levels)
            .chunkOf(minutes);

        Object[][] data = chunk.data();

        assertEquals(3, data.length);
        assertEquals(List.of(OPEN.toString(), 100.0), Arrays.asList(data[0][0], data[0][1]));
        assertEquals(List.of(OPEN.plusSeconds(3600).toString(), 200.0), Arrays.asList(data[1][0], data[1][1]));
        assertEquals(300.0, data[2][1]);

        // columns: time, price, extremes, buy orders, sell orders, level
        assertEquals(7.0, data[1][2]);
        assertEquals(5.0, data[1][3]);
        assertEquals(6.0, data[2][4]);
        assertNull(data[0][5]);
        assertEquals((double) (index + 70), data[1][5]); // read inside its own range, not at the hour's first index
        assertEquals((double) (index + 105), data[2][5]);
    }

    @Test
    void placesASingleLineAndItsAnnotationOnTheHoursTheyBelongTo() {
        List<Candle> minutes = new ArrayList<>();
        addMinutes(minutes, OPEN, 60, 0, 100);
        addMinutes(minutes, OPEN.plusSeconds(3600), 30, 60, 200);
        addMinutes(minutes, OPEN.plusSeconds(7200), 60, 90, 300);

        FunctionSeriesProvider line = new FunctionSeriesProvider("Line");
        line.addFunction(10, 100, x -> x * 2);
        line.addAnnotation(75, new Annotation("A", "in the second hour"));

        Object[][] data = new PriceChart(Candle::getCloseAsDouble)
            .setInterval(CandleInterval.HOUR)
            .setStepInterval(1)
            .addSeriesProvider(line)
            .chunkOf(minutes)
            .data();

        // columns: time, price, line, annotation, annotation text
        assertEquals(List.of(20.0, 120.0, 180.0), Arrays.asList(data[0][2], data[1][2], data[2][2]));
        assertEquals("A", data[1][3]);
        assertNull(data[0][3]);
        assertNull(data[2][3]);
    }

    /**
     * Drawn minute by minute, the axis is the old layout: a bar's row is its index less the first
     * one's. The charts every other simulation draws must come out the same as before.
     */
    @Test
    void minuteChartsComeOutAsTheyDidBefore() {
        List<Candle> minutes = risingMinutes();

        PointSeriesProvider points = new PointSeriesProvider("Points");
        points.addPoint(507, 1.0);
        points.addPoint(531, 2.0);

        FunctionGroupSeriesProvider lines = new FunctionGroupSeriesProvider("Lines");
        lines.addFunction(505, 520, x -> x / 2);
        lines.addFunction(525, 539, x -> -x);

        SeriesChunk expected = new SeriesDataAggregator()
            .addSeriesProvider(new CandleSeriesProvider(minutes, Candle::getCloseAsDouble))
            .addSeriesProvider(points)
            .addSeriesProvider(lines)
            .provide(500, 539, 1)
            .orElseThrow();
        SeriesChunk actual = new PriceChart(Candle::getCloseAsDouble)
            .setStepInterval(1)
            .addSeriesProvider(points)
            .addSeriesProvider(lines)
            .chunkOf(minutes);

        assertEquals(expected.columns(), actual.columns());
        assertArrayEquals(expected.data(), actual.data());
    }

    /**
     * At a wider step the price and the points are thinned as before. Lines are not compared: the
     * old layout rounded a segment's ends to the step on the raw index, which drew a line a row
     * before it began and read it outside its own range.
     */
    @Test
    void thinnedMinuteChartsComeOutAsTheyDidBefore() {
        List<Candle> minutes = risingMinutes();

        for (int step : new int[]{3, 10}) {
            PointSeriesProvider points = new PointSeriesProvider("Points");
            points.addPoint(507, 1.0);
            points.addPoint(531, 2.0);

            SeriesChunk expected = new SeriesDataAggregator()
                .addSeriesProvider(new CandleSeriesProvider(minutes, Candle::getCloseAsDouble))
                .addSeriesProvider(points)
                .provide(500, 539, step)
                .orElseThrow();
            SeriesChunk actual = new PriceChart(Candle::getCloseAsDouble)
                .setStepInterval(step)
                .addSeriesProvider(points)
                .chunkOf(minutes);

            assertArrayEquals(expected.data(), actual.data(), "step " + step);
        }
    }

    private static List<Candle> risingMinutes() {
        List<Candle> minutes = new ArrayList<>();

        for (int minute = 0; minute < 40; minute++) {
            minutes.add(Candle.of(new TimePoint(500 + minute, OPEN.plusSeconds(60L * minute)), 100 + minute));
        }

        return minutes;
    }

    private static void addMinutes(List<Candle> candles, Instant from, int count, long firstIndex, double price) {
        for (int minute = 0; minute < count; minute++) {
            candles.add(Candle.of(new TimePoint(firstIndex + minute, from.plusSeconds(60L * minute)), price));
        }
    }

    private static Operation order(Instant time, OperationType direction, double price) {
        return Operation.builder().date(time).direction(direction).price(Quotation.of(price)).build();
    }
}
