package com.siberalt.singularity.presenter.google.series;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class PointSeriesProviderTest {
    @Test
    void provideReturnsEmptyWhenNoPointsAdded() {
        PointSeriesProvider provider = new PointSeriesProvider("Empty Points Test");

        Optional<SeriesChunk> series = provider.provide(0, 10, 1);

        assertTrue(series.isEmpty());
    }

    @Test
    void provideFiltersPointsOutsideRange() {
        PointSeriesProvider provider = new PointSeriesProvider("Range Filter Test");
        provider.addPoint(5, 10.0);
        provider.addPoint(15, 20.0);

        Optional<SeriesChunk> series = provider.provide(0, 10, 1);

        assertTrue(series.isPresent());
        Object[][] data = series.get().data();

        assertEquals(11, data.length);
        assertEquals(10.0, data[5][0]);
        assertNull(data[0][0]);
        assertNull(data[10][0]);
    }

    @Test
    void provideAdjustsPointsToStepInterval() {
        PointSeriesProvider provider = new PointSeriesProvider("Step Interval Adjustment Test");
        provider.addPoint(2, 5.0);
        provider.addPoint(7, 10.0);

        Optional<SeriesChunk> series = provider.provide(0, 10, 5);

        assertTrue(series.isPresent());
        Object[][] data = series.get().data();

        assertEquals(3, data.length);
        assertEquals(5.0, data[0][0]); // Adjusted to x = 0
        assertEquals(10.0, data[1][0]); // Adjusted to x = 5
        assertNull(data[2][0]); // No point at x = 10
    }

    @Test
    void provideHandlesEmptyRange() {
        PointSeriesProvider provider = new PointSeriesProvider("Empty Range Test");
        provider.addPoint(5, 10.0);

        Optional<SeriesChunk> series = provider.provide(10, 5, 1);

        assertTrue(series.isEmpty());
    }

    @Test
    void provideIncludesOptionsInSeries() {
        PointSeriesProvider provider = new PointSeriesProvider("Options Test");
        provider.addPoint(5, 10.0);
        provider.setColor("#FF0000").setSize(10).setShape(Shape.SQUARE);

        Optional<SeriesChunk> series = provider.provide(0, 10, 1);

        assertTrue(series.isPresent());
        List<Map<String, Object>> optionsList = series.get().options();

        assertEquals(1, optionsList.size());
        Map<String, Object> options = optionsList.get(0);

        assertEquals("#FF0000", options.get("color"));
        assertEquals(10, options.get("pointSize"));
        assertEquals("square", options.get("pointShape"));
    }
}
