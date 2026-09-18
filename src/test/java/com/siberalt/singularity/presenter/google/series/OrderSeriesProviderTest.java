package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class OrderSeriesProviderTest {
    private static final Instant OPEN = Instant.parse("2023-01-01T00:00:00Z");

    @Test
    void provideReturnsEmptyWhenThereAreNoOrders() {
        OrderSeriesProvider provider = new OrderSeriesProvider(List.of());

        assertTrue(provider.provide(axis(OPEN, 3), 1).isEmpty());
    }

    @Test
    void provideSkipsOrdersOutsideTheAxis() {
        OrderSeriesProvider provider = new OrderSeriesProvider(List.of(
            order(OPEN, OperationType.BUY, 1)
        ));

        assertFalse(provider.provide(axis(OPEN.plusSeconds(86400), 3), 1).isPresent());
    }

    @Test
    void provideAddsBuyAndSellOrdersCorrectly() {
        OrderSeriesProvider provider = new OrderSeriesProvider(List.of(
            order(OPEN, OperationType.SELL, 100),
            order(OPEN.plusSeconds(60), OperationType.BUY, 200)
        ));

        Optional<SeriesChunk> result = provider.provide(axis(OPEN, 3), 1);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        // Buys first, then sells - the order the two point series are added in.
        assertEquals(2, chunk.columns().size());
        assertEquals(3, chunk.data().length);
        assertEquals(100.0, chunk.data()[0][1]);
        assertEquals(200.0, chunk.data()[1][0]);
    }

    /**
     * An order is drawn on the bar it was executed during, not on the nearest one: a trade at half
     * past belongs to the bar that was open at the time, however close the next bar's start is.
     */
    @Test
    void provideDrawsAnOrderOnTheBarItWasExecutedDuring() {
        OrderSeriesProvider provider = new OrderSeriesProvider(List.of(
            order(OPEN.plusSeconds(30), OperationType.SELL, 150),
            order(OPEN.plusSeconds(90), OperationType.BUY, 250)
        ));
        List<Candle> bars = new ArrayList<>(List.of(
            Candle.of(new TimePoint(0, OPEN), 100),
            Candle.of(new TimePoint(1, OPEN.plusSeconds(120)), 200)
        ));

        Optional<SeriesChunk> result = provider.provide(BarAxis.ofBars(bars), 1);

        assertTrue(result.isPresent());
        Object[][] data = result.get().data();

        assertEquals(2, data.length);
        assertEquals(250.0, data[0][0]);
        assertEquals(150.0, data[0][1]);
        assertNull(data[1][0]);
        assertNull(data[1][1]);
    }

    @Test
    void provideIncludesOutOfRangeOrdersWhenFlagIsSet() {
        OrderSeriesProvider provider = new OrderSeriesProvider(List.of(
            order(OPEN, OperationType.BUY, 300),
            order(OPEN.plusSeconds(86400), OperationType.SELL, 400)
        )).setIncludeOutOfRangeOrders(true);

        Optional<SeriesChunk> result = provider.provide(axis(OPEN.plusSeconds(60), 2), 1);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        assertEquals(2, chunk.columns().size());
        assertEquals(2, chunk.data().length);
        // Both are put on the edge bar of the side they fell off.
        assertEquals(300.0, chunk.data()[0][0]);
        assertEquals(400.0, chunk.data()[1][1]);
    }

    private static BarAxis axis(Instant from, int minutes) {
        List<Candle> bars = new ArrayList<>();

        for (int minute = 0; minute < minutes; minute++) {
            bars.add(Candle.of(new TimePoint(minute, from.plusSeconds(60L * minute)), 100 + minute));
        }

        return BarAxis.ofBars(bars);
    }

    private static Operation order(Instant executedAt, OperationType direction, double price) {
        return Operation.builder()
            .date(executedAt)
            .executedDate(executedAt)
            .direction(direction)
            .price(Quotation.of(price))
            .build();
    }
}
