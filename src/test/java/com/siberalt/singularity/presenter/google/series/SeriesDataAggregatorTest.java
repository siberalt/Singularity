package com.siberalt.singularity.presenter.google.series;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SeriesDataAggregatorTest {

    @Test
    void aggregateReturnsEmptyDataWhenNoProviders() {
        SeriesDataAggregator aggregator = new SeriesDataAggregator();
        Optional<SeriesChunk> result = aggregator.provide(0, 1000, 100);
        assertFalse(result.isPresent());
    }

    @Test
    void aggregateThrowsExceptionWhenDataRowMismatch() {
        SeriesProvider mockProvider = mock(SeriesProvider.class);
        SeriesChunk realChunk = new SeriesChunk(
            List.of(
                new Column(ColumnType.DATE, ColumnRole.DOMAIN, "col1"),
                new Column(ColumnType.NUMBER, ColumnRole.DATA, "col2")
            ),
            new Object[][]{{1, 2}, {3, 4}, {5, 6}, {7, 8}},
            List.of(Map.of("key1", "value1"))
        );

        when(mockProvider.provide(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(realChunk));
        SeriesDataAggregator aggregator = new SeriesDataAggregator().addSeriesProvider(mockProvider);

        assertThrows(IllegalStateException.class, () -> aggregator.provide(0, 400, 100));
    }

    @Test
    void aggregateCombinesDataFromMultipleProviders() {
        SeriesProvider provider1 = mock(SeriesProvider.class);
        SeriesProvider provider2 = mock(SeriesProvider.class);

        SeriesChunk chunk1 = new SeriesChunk(
            List.of(
                new Column(ColumnType.DATE, ColumnRole.DOMAIN, "col1"),
                new Column(ColumnType.NUMBER, ColumnRole.DATA, "col2")
            ),
            new Object[][]{{1, 2}, {3, 4}, {5, 6}},
            List.of(Map.of("key1", "value1"))
        );

        SeriesChunk chunk2 = new SeriesChunk(
            List.of(
                new Column(ColumnType.STRING, ColumnRole.ANNOTATION, "col1"),
                new Column(ColumnType.STRING, ColumnRole.ANNOTATION_TEXT, "col2")
            ),
            new Object[][]{{7, 8}, {9, 10}, {11, 12}},
            List.of(Map.of("key2", "value2"))
        );

        when(provider1.provide(0, 100, 50)).thenReturn(Optional.of(chunk1));
        when(provider2.provide(0, 100, 50)).thenReturn(Optional.of(chunk2));

        SeriesDataAggregator aggregator = new SeriesDataAggregator()
            .addSeriesProvider(provider1)
            .addSeriesProvider(provider2);

        Optional<SeriesChunk> result = aggregator.provide(0, 100, 50);
        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(4, chunk.columns().size());
        assertEquals(3, chunk.data().length);
        assertEquals(4, chunk.data()[0].length);
        assertEquals(2, chunk.options().size());

        assertArrayEquals(new Object[]{1, 2, 7, 8}, chunk.data()[0]);
        assertArrayEquals(new Object[]{3, 4, 9, 10}, chunk.data()[1]);
        assertArrayEquals(new Object[]{5, 6, 11, 12}, chunk.data()[2]);
    }

    @Test
    void aggregateSkipsEmptySeriesChunks() {
        SeriesProvider provider1 = mock(SeriesProvider.class);
        SeriesProvider provider2 = mock(SeriesProvider.class);

        SeriesChunk chunk1 = new SeriesChunk(
            List.of(
                new Column(ColumnType.DATE, ColumnRole.DOMAIN, "col1"),
                new Column(ColumnType.NUMBER, ColumnRole.DATA, "col2")
            ),
            new Object[][]{{1, 2}, {3, 4}, {5, 6}},
            List.of(Map.of("key1", "value1"))
        );

        when(provider1.provide(anyLong(), anyLong(), anyLong())).thenReturn(Optional.of(chunk1));
        when(provider2.provide(anyLong(), anyLong(), anyLong())).thenReturn(Optional.empty());

        SeriesDataAggregator aggregator = new SeriesDataAggregator()
            .addSeriesProvider(provider1)
            .addSeriesProvider(provider2);

        Optional<SeriesChunk> result = aggregator.provide(0, 100, 50);
        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.columns().size());
        assertEquals(3, chunk.data().length);
        assertEquals(2, chunk.data()[0].length);
        assertEquals(1, chunk.options().size());
    }
}
