package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CandleTest {
    private static final Instant TIME = Instant.parse("2024-03-01T10:00:00Z");

    @Test
    void equalCandlesHashEqually() {
        Candle a = new Candle("uid", new TimePoint(7, TIME),
            Quotation.of(1.5), Quotation.of(2.25), Quotation.of(3.0), Quotation.of(1.0), 100, 60, 40);
        Candle b = new Candle("uid", new TimePoint(7, TIME),
            Quotation.of(1.5), Quotation.of(2.25), Quotation.of(3.0), Quotation.of(1.0), 100, 60, 40);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.hashCode(), a.clone().hashCode());
    }

    @Test
    void candlesWithEqualQuotationsBuiltDifferentlyAreEqualAndHashEqually() {
        Candle a = new Candle("uid", new TimePoint(7, TIME),
            Quotation.of(1, 500_000_000),
            Quotation.of(2, 250_000_000),
            Quotation.of(3, 0),
            Quotation.of(1, -1_000_000_000),
            100, 60, 40);
        Candle b = new Candle("uid", new TimePoint(7, TIME),
            Quotation.of(new BigDecimal("1.50")),
            Quotation.of(1, 1_250_000_000),
            Quotation.of(4, -1_000_000_000),
            Quotation.ZERO,
            100, 60, 40);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equalCandlesCollapseInAHashSet() {
        Candle a = Candle.of(new TimePoint(1, TIME), "uid", 10, 1.5, 2.0, 1.0, 1.75);
        Candle sameAsA = Candle.of(new TimePoint(1, TIME), "uid", 10,
            Quotation.of(0, 1_500_000_000), Quotation.of(2L), Quotation.of(1, 0), Quotation.of("1.75"));
        Candle later = Candle.of(new TimePoint(2, TIME.plusSeconds(60)), "uid", 10, 1.5, 2.0, 1.0, 1.75);

        Set<Candle> set = new HashSet<>(List.of(a, sameAsA, later));

        assertEquals(2, set.size());
        assertTrue(set.contains(Candle.of(new TimePoint(1, TIME), "uid", 10, 1.5, 2.0, 1.0, 1.75)));
    }

    @Test
    void candlesDifferingInOneFieldAreNotEqual() {
        Candle base = new Candle("uid", new TimePoint(7, TIME),
            Quotation.of(1.5), Quotation.of(2.25), Quotation.of(3.0), Quotation.of(1.0), 100, 60, 40);

        assertNotEquals(base, new Candle("other", base.timePoint(),
            base.open(), base.close(), base.high(), base.low(), 100, 60, 40));
        assertNotEquals(base, new Candle("uid", new TimePoint(8, TIME),
            base.open(), base.close(), base.high(), base.low(), 100, 60, 40));
        assertNotEquals(base, new Candle("uid", base.timePoint(),
            base.open(), Quotation.of(2.26), base.high(), base.low(), 100, 60, 40));
        assertNotEquals(base, new Candle("uid", base.timePoint(),
            base.open(), base.close(), base.high(), base.low(), 100, 61, 39));
    }

    @Test
    void candleWithNullFieldsHashesLikeItsEqual() {
        Candle a = new Candle(null, new TimePoint(3), null, null, null, null, 0);
        Candle b = new Candle(null, new TimePoint(3), null, null, null, null, 0);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(Candle.EMPTY.hashCode(), Candle.EMPTY.clone().hashCode());
    }
}
