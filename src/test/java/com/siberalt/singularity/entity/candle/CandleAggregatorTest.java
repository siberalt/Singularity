package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CandleAggregatorTest {
    private static final long INSTRUMENT = 1;
    private static final Instant HOUR_START = Instant.parse("2021-06-03T10:00:00Z");

    private final CandleAggregator aggregator = new CandleAggregator();

    @Test
    void takesTheOpenOfTheFirstAndTheCloseOfTheLast() {
        List<Candle> hours = aggregator.aggregate(
            List.of(
                minute(0, 10, 12, 13, 9, 100),
                minute(1, 12, 11, 14, 8, 200),
                minute(2, 11, 15, 15, 11, 300)
            ),
            CandleInterval.HOUR
        );

        assertEquals(1, hours.size());

        Candle hour = hours.getFirst();
        assertEquals(Quotation.of(10), hour.open());
        assertEquals(Quotation.of(15), hour.close());
        assertEquals(Quotation.of(15), hour.high());
        assertEquals(Quotation.of(8), hour.low());
        assertEquals(600, hour.volume());
        assertEquals(HOUR_START, hour.getTime());
    }

    @Test
    void startsANewBarWhenTheClockCrossesTheInterval() {
        List<Candle> hours = aggregator.aggregate(
            List.of(minute(58, 10, 11, 11, 10, 1), minute(60, 20, 21, 21, 20, 2)),
            CandleInterval.HOUR
        );

        assertEquals(2, hours.size());
        assertEquals(Quotation.of(10), hours.getFirst().open());
        assertEquals(Quotation.of(20), hours.getLast().open());
    }

    /**
     * Bars are bucketed by the clock, not counted off, so an hour the market was shut for is an
     * hour that is missing rather than one that swallows the bars of the next.
     */
    @Test
    void leavesAGapWhereTheDataHasOne() {
        List<Candle> hours = aggregator.aggregate(
            List.of(minute(0, 10, 11, 11, 10, 1), minute(180, 20, 21, 21, 20, 2)),
            CandleInterval.HOUR
        );

        assertEquals(2, hours.size());
        assertEquals(HOUR_START, hours.getFirst().getTime());
        assertEquals(HOUR_START.plusSeconds(3 * 3600), hours.getLast().getTime());
    }

    @Test
    void returnsNothingForNothing() {
        assertEquals(List.of(), aggregator.aggregate(List.of(), CandleInterval.HOUR));
    }

    @Test
    void refusesAnIntervalWithNoFixedWidth() {
        List<Candle> minute = List.of(minute(0, 10, 11, 11, 10, 1));

        assertThrows(IllegalArgumentException.class, () -> aggregator.aggregate(minute, CandleInterval.MONTH));
        assertThrows(IllegalArgumentException.class, () -> aggregator.aggregate(minute, CandleInterval.UNSPECIFIED));
    }

    /**
     * Собранный бар нумеруется своим ведром, а не первой узкой свечой: это свой ряд, и в ряду минуток у
     * него нет места. Узкие свечи здесь вообще без индекса, и бар всё равно пронумерован - номер берётся
     * из времени.
     */
    @Test
    void numbersABarByItsBucketRatherThanByItsFirstCandle() {
        Candle hour = aggregator.aggregate(
            List.of(minute(0, 10, 12, 13, 9, 100), minute(1, 12, 11, 14, 8, 200)),
            CandleInterval.HOUR
        ).getFirst();

        assertEquals(Candle.DEFAULT_INDEX, minute(0, 10, 12, 13, 9, 100).getIndex());
        assertEquals(HOUR_START.toEpochMilli() / 3_600_000, hour.getIndex());
    }

    /** С какой стороны бар ни собирай - целиком или по одному, - номер у него один и тот же. */
    @Test
    void numbersTheSameBarAlikeWhicheverSideItIsAssembledFrom() {
        List<Candle> bucket = List.of(minute(0, 10, 12, 13, 9, 100), minute(1, 12, 11, 14, 8, 200));

        assertEquals(
            aggregator.aggregate(bucket, CandleInterval.HOUR).getFirst().getIndex(),
            aggregator.merge(bucket, CandleInterval.HOUR).getIndex()
        );
    }

    /**
     * Нумерация не плотная, и это не дефект: пропуск в данных остаётся пропуском и в номерах, иначе бар
     * после пропуска встал бы на место, которое ему не принадлежит.
     */
    @Test
    void leavesTheGapInTheNumbersToo() {
        List<Candle> hours = aggregator.aggregate(
            List.of(minute(0, 10, 12, 13, 9, 100), minute(3 * 60, 20, 22, 23, 19, 100)),
            CandleInterval.HOUR
        );

        assertEquals(3, hours.getLast().getIndex() - hours.getFirst().getIndex());
    }

    /**
     * Один и тот же час у разных бумаг получает один и тот же номер. Нумерация строк в базе идёт по
     * инструменту и такого никогда не давала.
     */
    @Test
    void numbersTheSameHourAlikeForEveryInstrument() {
        Candle mine = aggregator.merge(List.of(minute(0, 10, 12, 13, 9, 100)), CandleInterval.HOUR);
        Candle other = aggregator.merge(
            List.of(minute(INSTRUMENT + 1, 0, 50, 52, 53, 49, 100)), CandleInterval.HOUR);

        assertEquals(mine.getIndex(), other.getIndex());
    }

    private Candle minute(int offset, double open, double close, double high, double low, long volume) {
        return minute(INSTRUMENT, offset, open, close, high, low, volume);
    }

    private Candle minute(long instrument, int offset, double open, double close, double high, double low,
                          long volume) {
        return new Candle(
            instrument,
            new TimePoint(HOUR_START.plusSeconds(offset * 60L)),
            Quotation.of(open),
            Quotation.of(close),
            Quotation.of(high),
            Quotation.of(low),
            volume
        );
    }
}
