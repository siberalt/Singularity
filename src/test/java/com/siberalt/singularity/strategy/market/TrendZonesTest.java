package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Разметка истории на зоны режима: соседние бары одной стороны собираются в отрезки. */
class TrendZonesTest {
    private static final int WINDOW = 10;

    /** Одна сторона на всю историю - одна зона на всю историю. */
    @Test
    void makesOneZoneOutOfOneSide() {
        List<TrendZone> zones = TrendZones.of(bars(12), candles -> SteadySide.UP, WINDOW);

        assertEquals(1, zones.size());
        assertEquals(new TrendZone(0, 11, SteadySide.UP), zones.getFirst());
        assertEquals(12, zones.getFirst().bars());
        assertTrue(zones.getFirst().rising());
    }

    /** Смена стороны закрывает зону предыдущим баром и открывает новую этим же. */
    @Test
    void closesAZoneOnTheBarTheSideChanges() {
        int[] at = {0};
        List<TrendZone> zones = TrendZones.of(bars(9),
            candles -> at[0]++ < 4 ? SteadySide.UP : SteadySide.DOWN, WINDOW);

        assertEquals(List.of(new TrendZone(0, 3, SteadySide.UP), new TrendZone(4, 8, SteadySide.DOWN)),
            zones);
    }

    /** Флэт - такая же зона, а не промежуток между зонами. */
    @Test
    void marksFlatAsAZoneOfItsOwn() {
        double[] sides = {1, 1, 0, 0, 0, -1, -1};
        int[] at = {0};
        List<TrendZone> zones = TrendZones.of(bars(sides.length), candles -> sides[at[0]++], WINDOW);

        assertEquals(3, zones.size());
        assertTrue(zones.get(1).flat());
        assertEquals(3, zones.get(1).bars());
        assertTrue(zones.get(2).falling());
    }

    /** Пока мера молчит, баров в зонах нет: первая зона начинается с первого чтения. */
    @Test
    void startsTheFirstZoneAtTheFirstReading() {
        int[] at = {0};
        List<TrendZone> zones = TrendZones.of(bars(10),
            candles -> at[0]++ < 3 ? Double.NaN : SteadySide.DOWN, WINDOW);

        assertEquals(List.of(new TrendZone(3, 9, SteadySide.DOWN)), zones);
    }

    /**
     * Молчание посреди зоны её продолжает, а не закрывает: отсутствие чтения - не смена режима, и
     * граница по нему была бы выдумана.
     */
    @Test
    void carriesAZoneThroughASilentBar() {
        double[] sides = {1, 1, Double.NaN, 1, 1};
        int[] at = {0};

        assertEquals(List.of(new TrendZone(0, 4, SteadySide.UP)),
            TrendZones.of(bars(sides.length), candles -> sides[at[0]++], WINDOW));
    }

    /** Молчит всё время - ни одной зоны, а не зона без стороны. */
    @Test
    void makesNoZonesWhileTheMeasureNeverSpeaks() {
        assertEquals(List.of(), TrendZones.of(bars(10), candles -> Double.NaN, WINDOW));
    }

    /** Зоны идут по порядку, не пересекаются и покрывают всё от первого чтения до конца. */
    @Test
    void coversEverythingAfterTheFirstReadingWithoutGaps() {
        double[] sides = {Double.NaN, 1, 1, -1, 0, 0, 1};
        int[] at = {0};
        List<TrendZone> zones = TrendZones.of(bars(sides.length), candles -> sides[at[0]++], WINDOW);

        assertEquals(1, zones.getFirst().from(), "первое чтение - на баре 1");
        assertEquals(sides.length - 1, zones.getLast().to(), "последняя зона доходит до конца");

        for (int zone = 1; zone < zones.size(); zone++) {
            assertEquals(zones.get(zone - 1).to() + 1, zones.get(zone).from(),
                "между зонами нет ни дырки, ни наложения");
        }
    }

    /**
     * Коэффициенту показываются только бары до текущего включительно, и не больше окна. Заглядывания
     * вперёд в разметке нет - иначе зоны были бы картой задним числом, а не тем, что видело правило.
     */
    @Test
    void showsTheMeasureOnlyThePastAndNoMoreThanTheWindow() {
        List<Candle> bars = bars(20);
        List<int[]> seen = new ArrayList<>();

        TrendZones.of(bars, candles -> {
            seen.add(new int[]{candles.size(), (int) candles.getLast().getIndex()});

            return SteadySide.UP;
        }, WINDOW);

        assertEquals(20, seen.size(), "спрошен каждый бар");

        for (int at = 0; at < seen.size(); at++) {
            assertEquals(at, seen.get(at)[1], "последний показанный бар - текущий");
            assertEquals(Math.min(at + 1, WINDOW), seen.get(at)[0], "показано не больше окна");
        }
    }

    /**
     * Непрерывное чтение отвергается, а не размечается кое-как: граница зоны определяется равенством
     * меток, и у такой меры равных подряд значений не бывает - вышло бы по зоне на бар, а флэт со
     * своим «ровно ноль» не наступил бы никогда.
     */
    @Test
    void refusesAContinuousReadingInsteadOfMakingAZonePerBar() {
        double[] readings = {1, 1, 0.37, 1};
        int[] at = {0};

        assertThrows(IllegalArgumentException.class,
            () -> TrendZones.of(bars(readings.length), candles -> readings[at[0]++], WINDOW));
    }

    /** Классификатор не обязан быть трёхсторонним: меток может быть сколько угодно, лишь бы целые. */
    @Test
    void acceptsAClassifierWithMoreThanThreeLabels() {
        double[] sides = {2, 2, -3, -3};
        int[] at = {0};

        assertEquals(List.of(new TrendZone(0, 1, 2), new TrendZone(2, 3, -3)),
            TrendZones.of(bars(sides.length), candles -> sides[at[0]++], WINDOW));
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class, () -> TrendZones.of(null, candles -> 0, WINDOW));
        assertThrows(IllegalArgumentException.class, () -> TrendZones.of(bars(5), null, WINDOW));
        assertThrows(IllegalArgumentException.class, () -> TrendZones.of(bars(5), candles -> 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TrendZone(3, 2, SteadySide.UP));
        assertThrows(IllegalArgumentException.class, () -> new TrendZone(0, 1, Double.NaN));
    }

    private static List<Candle> bars(int count) {
        List<Candle> candles = new ArrayList<>(count);

        for (int at = 0; at < count; at++) {
            Quotation value = Quotation.of(100 + at);

            candles.add(new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
                value, value, value, value, 1));
        }

        return candles;
    }
}
