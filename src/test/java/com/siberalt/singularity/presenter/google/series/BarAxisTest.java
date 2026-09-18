package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BarAxisTest {
    private static final Instant NINE = Instant.parse("2025-03-03T09:00:00Z");

    /**
     * Minutes 09:00-09:02 (indices 100-102), 09:58-10:01 (103-106) and 11:30 (107): the ten o'clock
     * hour starts two indices after the nine o'clock one, and eleven starts one after that.
     */
    private static List<Candle> minutes() {
        List<Candle> minutes = new ArrayList<>();
        long index = 100;

        for (String time : List.of("09:00", "09:01", "09:02", "09:58", "09:59", "10:00", "10:01", "11:30")) {
            minutes.add(Candle.of(new TimePoint(index++, Instant.parse("2025-03-03T" + time + ":00Z")), 10));
        }

        return minutes;
    }

    @Test
    void rollsCandlesUpIntoOneRowPerBar() {
        BarAxis axis = BarAxis.of(minutes(), CandleInterval.HOUR);

        assertEquals(3, axis.size());
        assertEquals(List.of(100L, 105L, 107L), List.of(axis.indexAt(0), axis.indexAt(1), axis.indexAt(2)));
    }

    @Test
    void anIndexBelongsToTheBarItWasRolledInto() {
        BarAxis axis = BarAxis.of(minutes(), CandleInterval.HOUR);

        assertEquals(0, axis.rowOfIndex(104));
        assertEquals(1, axis.rowOfIndex(105));
        assertEquals(1, axis.rowOfIndex(106));
        assertEquals(2, axis.rowOfIndex(107));
        assertEquals(-1, axis.rowOfIndex(99));
        assertEquals(3, axis.rowOfIndex(108));
    }

    @Test
    void anInstantBelongsToTheHourItFallsInEvenWhereNoCandleWas() {
        BarAxis axis = BarAxis.of(minutes(), CandleInterval.HOUR);

        assertEquals(0, axis.rowOfTime(NINE.plusSeconds(30 * 60)));
        assertEquals(1, axis.rowOfTime(NINE.plusSeconds(110 * 60)));
        // Twenty past eleven: no candle yet, but inside the eleven o'clock bucket.
        assertEquals(2, axis.rowOfTime(NINE.plusSeconds(140 * 60)));
        assertEquals(-1, axis.rowOfTime(NINE.minusSeconds(1)));
        assertEquals(3, axis.rowOfTime(NINE.plusSeconds(180 * 60)));
    }

    @Test
    void barsTakenAsTheyAreCoverEverythingUpToTheNextOne() {
        List<Candle> bars = List.of(
            Candle.of(new TimePoint(0, NINE), 10),
            Candle.of(new TimePoint(60, NINE.plusSeconds(3600)), 10),
            Candle.of(new TimePoint(110, NINE.plusSeconds(7200)), 10)
        );
        BarAxis axis = BarAxis.ofBars(bars);

        assertEquals(0, axis.rowOfIndex(59));
        assertEquals(1, axis.rowOfIndex(109));
        assertEquals(2, axis.rowOfIndex(110));
        assertEquals(3, axis.rowOfIndex(111));
        // The last bar is taken to be as wide as the narrowest gap between two: an hour.
        assertEquals(2, axis.rowOfTime(NINE.plusSeconds(7200 + 3599)));
        assertEquals(3, axis.rowOfTime(NINE.plusSeconds(7200 + 3600)));
    }

    @Test
    void countsRowsOfDataAtAStep() {
        BarAxis axis = BarAxis.of(minutes(), CandleInterval.MIN_1);

        assertEquals(8, axis.rowsAt(1));
        assertEquals(4, axis.rowsAt(2));
        assertEquals(3, axis.rowsAt(3));
    }
}
