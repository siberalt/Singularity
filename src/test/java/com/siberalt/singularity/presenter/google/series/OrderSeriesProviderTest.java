package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class OrderSeriesProviderTest {
    @Test
    void provideReturnsEmptyWhenOrdersAndCandlesAreEmpty() {
        OrderSeriesProvider provider = new OrderSeriesProvider(List.of(), List.of());
        Optional<SeriesChunk> result = provider.provide(0, 1000, 100);
        assertTrue(result.isEmpty());
    }

    @Test
    void provideSkipsOrdersOutsideCandleRange() {
        Operation order = Operation.builder()
            .date(Instant.parse("2023-01-01T00:00:00Z"))
            .direction(OperationType.BUY)
            .price(Quotation.of(BigDecimal.ONE))
            .build();
        Candle candle = Candle.of(Instant.parse("2023-01-02T00:00:00Z"), 100, 10);

        List<Operation> orders = List.of(order);
        List<Candle> candles = List.of(candle);

        OrderSeriesProvider provider = new OrderSeriesProvider(orders, candles);
        Optional<SeriesChunk> result = provider.provide(0, 1000, 100);
        assertFalse(result.isPresent());
    }

    @Test
    void provideAddsBuyAndSellOrdersCorrectly() {
        List<Operation> orders = List.of(
            Operation.builder()
                .date(Instant.parse("2023-01-01T00:00:00Z"))
                .direction(OperationType.SELL)
                .price(Quotation.of(100))
                .build(),
            Operation.builder()
                .date(Instant.parse("2023-01-01T00:01:00Z"))
                .direction(OperationType.BUY)
                .price(Quotation.of(200))
                .build()
        );

        Candle candle1 = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 150, 10);
        Candle candle2 = Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 150, 10);
        Candle candle3 = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 150, 10);

        List<Candle> candles = List.of(candle1, candle2, candle3);
        OrderSeriesProvider provider = new OrderSeriesProvider(orders, candles);
        Optional<SeriesChunk> result = provider.provide(0, 4, 1);
        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();
        assertEquals(2, chunk.columns().size());
        assertEquals(5, chunk.data().length);
        assertEquals(100.0, chunk.data()[0][1]);
        assertEquals(200.0, chunk.data()[1][0]);
    }

    @Test
    void provideHandlesOrdersWithClosestCandleIndex() {
        Operation order1 = Operation.builder()
            .date(Instant.parse("2023-01-01T00:00:30Z"))
            .direction(OperationType.SELL)
            .price(Quotation.of(150))
            .build();

        Operation order2 = Operation.builder()
            .date(Instant.parse("2023-01-01T00:01:30Z"))
            .direction(OperationType.BUY)
            .price(Quotation.of(250))
            .build();

        Candle candle1 = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 100, 10);
        Candle candle2 = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 200, 20);

        List<Operation> orders = List.of(order1, order2);
        List<Candle> candles = List.of(candle1, candle2);
        OrderSeriesProvider provider = new OrderSeriesProvider(orders, candles);
        Optional<SeriesChunk> result = provider.provide(0, 2, 1);
        assertTrue(result.isPresent());

        SeriesChunk chunk = result.get();
        assertEquals(2, chunk.columns().size());
        assertEquals(3, chunk.data().length);
        assertEquals(150.0, chunk.data()[0][1]);
        assertEquals(250.0, chunk.data()[1][0]);
    }

    @Test
    void provideIncludesOutOfRangeOrdersWhenFlagIsSet() {
        Operation outOfRangeOrder1 = Operation.builder()
            .date(Instant.parse("2023-01-01T00:00:00Z"))
            .direction(OperationType.BUY)
            .price(Quotation.of(300))
            .build();

        Operation outOfRangeOrder2 = Operation.builder()
            .date(Instant.parse("2023-01-02T00:00:00Z"))
            .direction(OperationType.SELL)
            .price(Quotation.of(400))
            .build();

        Candle candle1 = Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 150, 10);
        Candle candle2 = Candle.of(Instant.parse("2023-01-01T00:02:00Z"), 150, 10);

        List<Operation> orders = List.of(outOfRangeOrder1, outOfRangeOrder2);
        List<Candle> candles = List.of(candle1, candle2);

        OrderSeriesProvider provider = new OrderSeriesProvider(orders, candles)
            .setIncludeOutOfRangeOrders(true);

        Optional<SeriesChunk> result = provider.provide(0, 2, 1);
        assertTrue(result.isPresent());

        SeriesChunk chunk = result.get();
        assertEquals(2, chunk.columns().size());
        assertEquals(3, chunk.data().length);

        // Verify that out-of-range orders are placed at the edges
        assertEquals(300.0, chunk.data()[0][0]); // First candle index for BUY
        assertEquals(400.0, chunk.data()[1][1]); // Last candle index for SELL
    }
}
