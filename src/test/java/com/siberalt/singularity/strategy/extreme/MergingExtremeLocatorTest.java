package com.siberalt.singularity.strategy.extreme;

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

/** Зигзаг из двух односторонних локаторов: развороты обязаны чередоваться. */
class MergingExtremeLocatorTest {
    private static final int VICINITY = 2;

    /** Две ноги вверх-вниз-вверх дают два разворота, и они разного сорта. */
    @Test
    void findsTurningPointsOfBothKinds() {
        List<Candle> candles = series(100, 101, 102, 103, 104, 103, 102, 101, 100, 101, 102, 103, 104);
        List<Candle> pivots = MergingExtremeLocator.ofPivots(VICINITY).locate(candles);

        assertEquals(2, pivots.size());
        assertEquals(104, pivots.get(0).getCloseAsDouble(), 1e-9, "верх на четвёртом баре");
        assertEquals(100, pivots.get(1).getCloseAsDouble(), 1e-9, "низ на восьмом");
    }

    /**
     * Возвращается всё, что нашли вложенные локаторы, а не прореженный зигзаг: два максимума подряд
     * остаются оба. Локатора спрашивают, где экстремумы, и выбрасывать найденное он не вправе.
     */
    @Test
    void keepsEverythingBothLocatorsFound() {
        List<Candle> candles = series(
            100, 101, 102, 110, 102, 101, 102, 120, 102, 101, 100, 99, 98, 97, 96);
        List<Candle> pivots = new MergingExtremeLocator(
            PivotPointExtremeLocator.ofMinimums(VICINITY).withoutGrouping(),
            PivotPointExtremeLocator.ofMaximums(VICINITY).withoutGrouping()).locate(candles);

        assertTrue(pivots.stream().anyMatch(pivot -> pivot.getCloseAsDouble() == 110),
            "низший максимум серии тоже на месте");
        assertTrue(pivots.stream().anyMatch(pivot -> pivot.getCloseAsDouble() == 120),
            "и высший");
    }

    /**
     * А прореживание - дело вложенного локатора, и он его делает сам: {@code ofPivots} оставляет от
     * двух близких максимумов высший, потому что PivotPointExtremeLocator заканчивает группировкой.
     * Повторять это в слиянии было бы вторым местом, делающим то же самое.
     */
    @Test
    void leavesThinningToTheLocatorInside() {
        List<Candle> candles = series(
            100, 101, 102, 110, 102, 101, 102, 120, 102, 101, 100, 99, 98, 97, 96);
        List<Candle> pivots = MergingExtremeLocator.ofPivots(VICINITY).locate(candles);

        assertTrue(pivots.stream().noneMatch(pivot -> pivot.getCloseAsDouble() == 110),
            "сгруппирован вложенным локатором");
    }

    /** Порядок - по возрастанию индекса свечи, как бы ни отдали вложенные локаторы. */
    @Test
    void returnsThemInBarOrder() {
        List<Candle> pivots = MergingExtremeLocator.ofPivots(VICINITY).locate(wave());

        assertTrue(pivots.size() >= 3, "разворотов нашлось " + pivots.size());

        for (int at = 1; at < pivots.size(); at++) {
            assertTrue(pivots.get(at - 1).getIndex() <= pivots.get(at).getIndex(),
                "порядок нарушен на " + at);
        }
    }

    /**
     * Волатильность не участвует: умножьте весь ряд на сто, и развороты окажутся на тех же барах.
     * У {@link ZigZagExtremeLocator} с его порогом в волатильностях так не выйдет.
     */
    @Test
    void findsTheSameBarsWhateverTheScale() {
        MergingExtremeLocator locator = MergingExtremeLocator.ofPivots(VICINITY);
        List<Candle> plain = wave();
        List<Candle> scaled = new ArrayList<>();

        for (int at = 0; at < plain.size(); at++) {
            scaled.add(candle(at, plain.get(at).getCloseAsDouble() * 100));
        }

        assertEquals(locator.locate(plain).stream().map(Candle::getIndex).toList(),
            locator.locate(scaled).stream().map(Candle::getIndex).toList());
    }

    /** Окрестность и есть задержка: последние её бары разворотом не назовутся. */
    @Test
    void leavesTheTailOfTheWindowUnjudged() {
        List<Candle> candles = series(100, 101, 102, 103, 104, 103, 102);
        List<Candle> pivots = MergingExtremeLocator.ofPivots(3).locate(candles);

        assertTrue(pivots.isEmpty(),
            "у вершины справа всего два бара при окрестности в три, судить её нечем");
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        ExtremeLocator any = PivotPointExtremeLocator.ofMinimums(VICINITY);

        assertThrows(IllegalArgumentException.class,
            () -> new MergingExtremeLocator(null, any));
        assertThrows(IllegalArgumentException.class,
            () -> new MergingExtremeLocator(any, null));
        assertEquals(List.of(), MergingExtremeLocator.ofPivots(VICINITY).locate(null));
        assertEquals(List.of(), MergingExtremeLocator.ofPivots(VICINITY).locate(List.of()));
    }

    /** Ход с несколькими разворотами разной глубины. */
    private static List<Candle> wave() {
        return series(100, 102, 104, 106, 108, 106, 104, 102, 100, 102, 104, 106, 108, 110,
            108, 106, 104, 102, 100, 98, 96, 98, 100, 102, 104);
    }

    private static List<Candle> series(double... prices) {
        List<Candle> candles = new ArrayList<>(prices.length);

        for (int at = 0; at < prices.length; at++) {
            candles.add(candle(at, prices[at]));
        }

        return candles;
    }

    private static Candle candle(int at, double price) {
        Quotation value = Quotation.of(price);

        return new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
            value, value, value, value, 1);
    }
}
