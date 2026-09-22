package com.siberalt.singularity.strategy.level.zone;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.ExtremeWeigher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WeightedDBSCANZoneDetectorTest {
    private static final Instant START = Instant.parse("2023-01-02T07:00:00Z");

    /** Границы - по ценам экстремумов с запасом в полрадиуса, а не по верху разворотного бара. */
    @Test
    void aZoneSpansItsPricesWithABuffer() {
        List<Candle> candles = candles(
            bar(0, 100.0, 104.0), bar(10, 100.5, 101.0), bar(20, 100.2, 100.8), bar(30, 110.0, 111.0));

        List<Zone> zones = detector().detect(candles);

        assertEquals(1, zones.size());
        assertEquals(99.5, zones.getFirst().low(), 1e-9);
        assertEquals(101.0, zones.getFirst().high(), 1e-9);
        assertEquals((100.0 + 100.5 + 100.2) / 3, zones.getFirst().price(), 1e-9);
        assertEquals(3, zones.getFirst().touchesCount());
        assertEquals(0, zones.getFirst().pointFrom().index());
        assertEquals(20, zones.getFirst().pointTo().index());
    }

    @Test
    void twoGroupsGiveTwoZonesTheHeavierFirst() {
        List<Candle> candles = candles(
            bar(0, 100.0, 100.5), bar(10, 100.3, 100.8), bar(20, 100.1, 100.6),
            bar(30, 120.0, 120.5), bar(40, 120.2, 120.7), bar(50, 119.9, 120.4), bar(60, 120.1, 120.6));

        List<Zone> zones = detector().detect(candles);

        assertEquals(2, zones.size());
        assertEquals(4, zones.get(0).touchesCount());
        assertTrue(zones.get(0).contains(120.0));
        assertEquals(3, zones.get(1).touchesCount());
        assertTrue(zones.get(0).strength() > zones.get(1).strength());
    }

    /**
     * Три точки на одной цене - зона, пока они весят как все. Если остальные точки окна тяжелее их в сто
     * раз, их вместе меньше, чем одна средняя точка, и ядра не набирается.
     */
    @Test
    void lightPointsMakeNoCore() {
        List<Candle> candles = candles(
            bar(0, 100.0, 100.5), bar(10, 100.2, 100.7), bar(20, 100.1, 100.6),
            bar(30, 130.0, 130.5), bar(40, 140.0, 140.5), bar(50, 150.0, 150.5));
        ExtremeWeigher farIsHeavy = (extremes, window) -> extreme -> extreme.getLowAsDouble() > 120 ? 10.0 : 0.1;

        assertEquals(1, detector().detect(candles).size());
        assertTrue(detector().setWeigher(farIsHeavy).detect(candles).isEmpty());
    }

    /**
     * Тяжёлая точка видит дальше: с дальностью её радиус вдвое больше базового, и две лёгкие точки в
     * полутора радиусах от неё становятся её зоной. Без дальности она одна, а одной точки мало.
     */
    @Test
    void aHeavyPointReachesFurther() {
        List<Candle> candles = candles(bar(0, 100.0, 100.5), bar(10, 101.5, 102.0), bar(20, 101.7, 102.2));
        ExtremeWeigher firstIsHeavy = (extremes, window) -> extreme -> extreme.getLowAsDouble() < 101 ? 10.0 : 1.0;

        assertTrue(detector().setWeigher(firstIsHeavy).setMinWeight(2.5).detect(candles).isEmpty());

        List<Zone> zones = detector().setWeigher(firstIsHeavy).setMinWeight(2.5).setReach(1).detect(candles);

        assertEquals(1, zones.size());
        assertEquals(3, zones.getFirst().touchesCount());
    }

    @Test
    void theCentreLeansTowardsTheHeavyPoint() {
        List<Candle> candles = candles(bar(0, 100.0, 100.5), bar(10, 100.6, 101.1), bar(20, 100.8, 101.3));
        ExtremeWeigher firstIsHeavy = (extremes, window) -> extreme -> extreme.getLowAsDouble() < 100.5 ? 10.0 : 1.0;

        Zone zone = detector().setWeigher(firstIsHeavy).setMinWeight(1).detect(candles).getFirst();

        assertEquals((100.0 * 10 + 100.6 + 100.8) / 12, zone.price(), 1e-9);
    }

    /**
     * Точка между двумя плотными группами - сосед обеих, но сама не ядро. Она входит краем в одну из
     * зон, и зоны остаются двумя, а не склеиваются через неё.
     */
    @Test
    void aBorderPointDoesNotGlueTwoZones() {
        List<Candle> candles = candles(
            bar(0, 100.0, 100.1), bar(10, 100.3, 100.4), bar(20, 100.6, 100.7), bar(30, 100.9, 101.0),
            bar(40, 101.8, 101.9),
            bar(50, 102.7, 102.8), bar(60, 103.0, 103.1), bar(70, 103.3, 103.4), bar(80, 103.6, 103.7));

        List<Zone> zones = detector().setMinWeight(4).detect(candles);

        assertEquals(2, zones.size());
        assertEquals(9, zones.get(0).touchesCount() + zones.get(1).touchesCount());
        assertTrue(zones.stream().noneMatch(zone -> zone.contains(100.0) && zone.contains(103.6)));
    }

    /** Геометрия тестов - без дальности, чтобы радиус был ровно единицей. */
    private static WeightedDBSCANZoneDetector detector() {
        return new WeightedDBSCANZoneDetector(window -> window)
            .setVolatilityTolerance(window -> 1, 1)
            .setReach(0)
            .setBuffer(0.5)
            .setMinWeight(3)
            .setMinPoints(3);
    }

    private record Bar(long index, double low, double high) {
    }

    private static Bar bar(long index, double low, double high) {
        return new Bar(index, low, high);
    }

    private static List<Candle> candles(Bar... bars) {
        List<Candle> candles = new ArrayList<>();

        for (Bar bar : bars) {
            candles.add(new Candle(1, new TimePoint(bar.index(), START.plusSeconds(3600L * bar.index())),
                Quotation.of(bar.low()), Quotation.of(bar.high()), Quotation.of(bar.high()), Quotation.of(bar.low()), 1));
        }

        return candles;
    }
}
