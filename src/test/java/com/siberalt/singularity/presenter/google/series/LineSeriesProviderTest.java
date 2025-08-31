package com.siberalt.singularity.presenter.google.series;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LineSeriesProviderTest {
    @Test
    void provideHandlesEmptyLinesAndAnnotations() {
        LineSeriesProvider provider = new LineSeriesProvider("Empty Test");

        Optional<SeriesChunk> series = provider.provide(0, 10, 1);

        assertFalse(series.isPresent());
    }

    @Test
    void provideHandlesSinglePointRange() {
        LineSeriesProvider provider = new LineSeriesProvider("Single Point Test");
        provider.addLine(5, 15, x -> x * 2);

        Optional<SeriesChunk> series = provider.provide(10, 10, 1);

        assertTrue(series.isPresent());
        assertEquals(1, series.get().data().length);
        assertEquals(20.0, series.get().data()[0][0]);
    }

    @Test
    void provideHandlesStepIntervalLargerThanRange() {
        LineSeriesProvider provider = new LineSeriesProvider("Large Step Interval Test");
        provider.addLine(0, 20, x -> x + 1);

        Optional<SeriesChunk> series = provider.provide(0, 10, 15);

        assertTrue(series.isPresent());
        assertEquals(1, series.get().data().length);
        assertEquals(1.0, series.get().data()[0][0]);
    }

    @Test
    void provideHandlesAnnotationsWithoutLines() {
        LineSeriesProvider provider = new LineSeriesProvider("Annotations Only Test");
        provider.addAnnotation(5, new Annotation("Label", "Text"));

        Optional<SeriesChunk> series = provider.provide(0, 10, 1);

        assertFalse(series.isPresent());
    }

    @Test
    void provideHandlesNoAnnotationsInRange() {
        LineSeriesProvider provider = new LineSeriesProvider("No Annotations in Range Test");
        provider.addAnnotation(15, new Annotation("Out of Range"));
        provider.addLine(0, 10, x -> x); // Line: y = x

        Optional<SeriesChunk> series = provider.provide(0, 10, 1);

        assertTrue(series.isPresent());
        assertEquals(11, series.get().data().length);
        for (int i = 0; i <= 10; i++) {
            assertNull(series.get().data()[i][1]);
            assertNull(series.get().data()[i][2]);
        }
    }

    @Test
    void provideHandlesStandardCase() {
        LineSeriesProvider provider = new LineSeriesProvider("Standard Case Test");
        provider.addLine(0, 10, x -> x * 2); // Line: y = 2x
        provider.addAnnotation(5, new Annotation("Midpoint", "This is the midpoint"));

        Optional<SeriesChunk> series = provider.provide(0, 10, 1);

        assertTrue(series.isPresent());
        Object[][] data = series.get().data();

        // Verify data length
        assertEquals(11, data.length);

        // Verify line values
        for (int i = 0; i <= 10; i++) {
            assertEquals(i * 2.0, data[i][0]);
        }

        // Verify annotation at x = 5
        assertEquals("Midpoint", data[5][1]);
        assertEquals("This is the midpoint", data[5][2]);
    }

    @Test
    void provideHandlesStepIntervalOfFive() {
        LineSeriesProvider provider = new LineSeriesProvider("Step Interval Test");
        provider.addLine(0, 20, x -> x + 1); // Line: y = x + 1
        provider.addAnnotation(10, new Annotation("Annotation", "At x = 10"));

        Optional<SeriesChunk> series = provider.provide(0, 20, 5);

        assertTrue(series.isPresent());
        Object[][] data = series.get().data();

        // Verify data length
        assertEquals(5, data.length);

        // Verify line values
        assertEquals(1.0, data[0][0]); // x = 0
        assertEquals(6.0, data[1][0]); // x = 5
        assertEquals(11.0, data[2][0]); // x = 10
        assertEquals(16.0, data[3][0]); // x = 15
        assertEquals(21.0, data[4][0]); // x = 20

        // Verify annotation at x = 10
        assertNull(data[0][1]); // No annotation at x = 0
        assertNull(data[1][1]); // No annotation at x = 5
        assertEquals("Annotation", data[2][1]); // Annotation at x = 10
        assertEquals("At x = 10", data[2][2]); // Annotation text at x = 10
        assertNull(data[3][1]); // No annotation at x = 15
        assertNull(data[4][1]); // No annotation at x = 20
    }

    @Test
    void provideHandlesMultipleLines() {
        LineSeriesProvider provider = new LineSeriesProvider("Multiple Lines Test");
        provider.addLine(0, 10, x -> x * 2); // Line 1: y = 2x
        provider.addLine(15, 25, x -> x + 5); // Line 2: y = x + 5

        Optional<SeriesChunk> series = provider.provide(0, 25, 5);

        assertTrue(series.isPresent());
        Object[][] data = series.get().data();

        // Verify data length
        assertEquals(6, data.length);

        // Verify line 1 values
        assertEquals(0.0, data[0][0]); // x = 0
        assertEquals(10.0, data[1][0]); // x = 5
        assertEquals(20.0, data[2][0]); // x = 10

        // Verify line 2 values
        assertEquals(20.0, data[3][0]); // x = 15
        assertEquals(25.0, data[4][0]); // x = 20
        assertEquals(30.0, data[5][0]); // x = 25
    }

    @Test
    void addLineThrowsExceptionForOverlappingSegments() {
        LineSeriesProvider provider = new LineSeriesProvider("Overlapping Lines Test");
        provider.addLine(0, 10, x -> x * 2); // First line: y = 2x

        // Attempt to add an overlapping line
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            provider.addLine(5, 15, x -> x + 1); // Overlaps with the first line
        });

        assertEquals("Line segment already exists or overlaps with another segment", exception.getMessage());
    }
}