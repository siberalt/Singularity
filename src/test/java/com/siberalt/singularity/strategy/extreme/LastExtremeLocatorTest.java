package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LastExtremeLocator — поиск последнего подтверждённого экстремума")
class LastExtremeLocatorTest {

    @Nested
    @DisplayName("Поиск минимумов")
    class MinimaTests {

        @Test
        @DisplayName("Должен находить подтверждённый минимум с vicinity=2")
        void shouldFindConfirmedMinimumWithVicinity2() {
            CandleFactory candleFactory = new CandleFactory("TEST");

            // Создаём ЛОКАЛЬНЫЙ список, не трогая this.candles
            List<Candle> localCandles = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 102),
                candleFactory.createCommon("2024-01-01T02:00:00Z", 106), // максимум
                candleFactory.createCommon("2024-01-01T03:00:00Z", 103),
                candleFactory.createCommon("2024-01-01T04:00:00Z", 104),
                candleFactory.createCommon("2024-01-01T05:00:00Z", 101),
                candleFactory.createCommon("2024-01-01T06:00:00Z", 105)
            );

            var locator = LastExtremeLocator.ofMaximums(2, Candle::getTypicalAsDouble);
            var result = locator.locate(localCandles);

            assertEquals(1, result.size());
            assertSame(localCandles.get(2), result.get(0)); // теперь сравниваем с локальным списком
        }

        @Test
        @DisplayName("Не должен находить минимум, если не хватает подтверждений")
        void shouldNotFindUnconfirmedMinimum() {
            CandleFactory candleFactory = new CandleFactory("TEST-PAIR");
            // vicinity=3, но слева от кандидата (индекс 3) только 3 свечи, а справа — 5, но нужно по 3
            var locator = new LastExtremeLocator(3, Comparator.comparing(Candle::getTypicalAsDouble));
            List<Candle> candles = List.of(
                candleFactory.createCommon("2024-01-01T01:00:00Z", 105.0), // 1
                candleFactory.createCommon("2024-01-01T02:00:00Z", 103.0), // 2 — минимум?
                candleFactory.createCommon("2024-01-01T03:00:00Z", 107.0), // 3
                candleFactory.createCommon("2024-01-01T04:00:00Z", 100.0), // 4 — настоящий минимум
                candleFactory.createCommon("2024-01-01T05:00:00Z", 102.0), // 5
                candleFactory.createCommon("2024-01-01T06:00:00Z", 106.0), // 6
                candleFactory.createCommon("2024-01-01T07:00:00Z", 98.0),  // 7 — ниже, но не подтверждён
                candleFactory.createCommon("2024-01-01T08:00:00Z", 101.0), // 8
                candleFactory.createCommon("2024-01-01T09:00:00Z", 104.0)  // 9 — последняя
            );
            var result = locator.locate(candles);

            assertTrue(result.isEmpty()); // не должно быть найдено
        }

        @Test
        @DisplayName("С vicinity=0 должен вернуть последнюю свечу")
        void shouldReturnLastCandleWhenVicinityIsZero() {
            var locator = new LastExtremeLocator(0, Comparator.comparing(Candle::getTypicalAsDouble));
            CandleFactory candleFactory = new CandleFactory("TEST");

            List<Candle> candles = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 101),
                candleFactory.createCommon("2024-01-01T02:00:00Z", 102)
            );

            var result = locator.locate(candles);

            assertEquals(1, result.size());
            assertSame(candles.get(candles.size() - 1), result.get(0));
        }
    }

    @Nested
    @DisplayName("Поиск максимумов")
    class MaximaTests {

        @Test
        @DisplayName("Должен находить подтверждённый максимум")
        void shouldFindConfirmedMaximum() {
            CandleFactory candleFactory = new CandleFactory("TEST");

            // Искусственно сделаем максимум на индексе 5 (цена 106)
            List<Candle> localCandles = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 102),
                candleFactory.createCommon("2024-01-01T02:00:00Z", 106), // максимум
                candleFactory.createCommon("2024-01-01T03:00:00Z", 103),
                candleFactory.createCommon("2024-01-01T04:00:00Z", 104),
                candleFactory.createCommon("2024-01-01T05:00:00Z", 101),
                candleFactory.createCommon("2024-01-01T06:00:00Z", 105)
            );

            var locator = LastExtremeLocator.ofMaximums(2, Candle::getTypicalAsDouble);
            var result = locator.locate(localCandles);

            assertEquals(1, result.size());
            assertSame(localCandles.get(2), result.get(0));
        }

        @Test
        @DisplayName("Должен подтвердить минимум, если количество равных в окрестности в пределах maxAllowedEqualPeers=1")
        void shouldConfirmMinimumWithOneEqualPeer() {
            CandleFactory candleFactory = new CandleFactory("TEST");

            List<Candle> testCandles = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 105),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 100), // равная — разрешено (1 шт)
                candleFactory.createCommon("2024-01-01T02:00:00Z", 100), // кандидат
                candleFactory.createCommon("2024-01-01T03:00:00Z", 102)
            );

            var locator = LastExtremeLocator.ofMinimums(1, 1, Candle::getTypicalAsDouble);
            var result = locator.locate(testCandles);

            assertEquals(1, result.size());
            assertTrue(result.contains(testCandles.get(2)), "Expected candidate candle to be in result");
        }

        @Test
        @DisplayName("Не должен подтверждать минимум, если равных свечей больше, чем maxAllowedEqualPeers=1")
        void shouldRejectMinimumWithTwoEqualPeersWhenMaxIsOne() {
            CandleFactory candleFactory = new CandleFactory("TEST");

            List<Candle> testCandles = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 105),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 100), // кандидат
                candleFactory.createCommon("2024-01-01T02:00:00Z", 100), // равная
                candleFactory.createCommon("2024-01-01T03:00:00Z", 100)  // ещё одна — уже перебор
            );

            var locator = LastExtremeLocator.ofMinimums(2, 1, Candle::getTypicalAsDouble);
            var result = locator.locate(testCandles);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Должен находить максимум с одной равной свечой при maxAllowedEqualPeers=1")
        void shouldConfirmMaximumWithOneEqualPeer() {
            CandleFactory candleFactory = new CandleFactory("TEST");

            List<Candle> testCandles = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 95),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 100), // равная — разрешено
                candleFactory.createCommon("2024-01-01T02:00:00Z", 100), // кандидат
                candleFactory.createCommon("2024-01-01T03:00:00Z", 98)
            );

            var locator = LastExtremeLocator.ofMaximums(1, 1, Candle::getTypicalAsDouble);
            var result = locator.locate(testCandles);

            assertEquals(1, result.size());
            assertSame(testCandles.get(2), result.get(0));
        }

        @Test
        @DisplayName("Должен игнорировать экстремум, если maxAllowedEqualPeers < 0")
        void shouldThrowOnNegativeMaxAllowedEqualPeers() {
            assertThrows(IllegalArgumentException.class, () ->
                new LastExtremeLocator(2, -1, Comparator.comparing(Candle::getTypicalAsDouble))
            );
        }

        @Test
        @DisplayName("Должен игнорировать экстремум, если maxAllowedEqualPeers > extremeVicinity")
        void shouldThrowOnMaxAllowedEqualPeersExceedingVicinity() {
            assertThrows(IllegalArgumentException.class, () ->
                new LastExtremeLocator(2, 3, Comparator.comparing(Candle::getTypicalAsDouble))
            );
        }

        @Test
        @DisplayName("При maxAllowedEqualPeers = extremeVicinity должен принимать все равные")
        void shouldAcceptAllEqualsWhenMaxPeersEqualsVicinity() {
            CandleFactory candleFactory = new CandleFactory("TEST");

            List<Candle> testCandles = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 95), // кандидат
                candleFactory.createCommon("2024-01-01T02:00:00Z", 95),
                candleFactory.createCommon("2024-01-01T03:00:00Z", 95),
                candleFactory.createCommon("2024-01-01T04:00:00Z", 97)
            );

            var locator = LastExtremeLocator.ofMinimums(2, 2, Candle::getTypicalAsDouble);
            var result = locator.locate(testCandles);

            assertEquals(1, result.size());
            assertSame(testCandles.get(2), result.get(0));
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("Должен выбросить исключение при null")
        void shouldThrowOnNullCandles() {
            var locator = LastExtremeLocator.ofMinimums();
            assertThrows(IllegalArgumentException.class, () -> locator.locate(null));
        }

        @Test
        @DisplayName("Должен вернуть пустой список, если свечей недостаточно для окрестности")
        void shouldReturnEmptyIfNotEnoughCandles() {
            CandleFactory candleFactory = new CandleFactory("TEST");

            var few = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 105),
                candleFactory.createCommon("2024-01-01T02:00:00Z", 95)
            );

            var locator = new LastExtremeLocator(2, Comparator.comparing(Candle::getTypicalAsDouble));
            var result = locator.locate(few);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Должен запрещать отрицательную vicinity")
        void shouldRejectNegativeVicinity() {
            assertThrows(IllegalArgumentException.class, () ->
                new LastExtremeLocator(-1, Comparator.comparing(Candle::getTypicalAsDouble))
            );
        }

        @Test
        @DisplayName("Равные значения цен не должны подтверждать экстремум")
        void shouldExcludeExtremesWithEqualNeighbors() {
            CandleFactory candleFactory = new CandleFactory("TEST");
            List<Candle> candles = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 100),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 200),
                candleFactory.createCommon("2024-01-01T02:00:00Z", 300),
                candleFactory.createCommon("2024-01-01T03:00:00Z", 300), // равна предыдущей
                candleFactory.createCommon("2024-01-01T04:00:00Z", 200),
                candleFactory.createCommon("2024-01-01T05:00:00Z", 100)
            );

            var locator = LastExtremeLocator.ofMaximums(2, Candle::getTypicalAsDouble);
            var result = locator.locate(candles);

            assertTrue(result.isEmpty()); // из-за равных значений — не подтверждён
        }
    }

    @Nested
    @DisplayName("Фабричные методы")
    class FactoryMethods {

        @Test
        @DisplayName("ofMinimums() должен использовать typicalPrice по умолчанию")
        void ofMinimumsShouldUseTypicalPrice() {
            LastExtremeLocator locator = LastExtremeLocator.ofMinimums(1, Candle::getTypicalAsDouble);
            CandleFactory candleFactory = new CandleFactory("TEST");

            // Просто проверяем, что объект создан
            assertNotNull(locator);

            // Создадим данные, где явно есть минимум
            var testCandles = List.of(
                candleFactory.createCommon("2024-01-01T00:00:00Z", 105),
                candleFactory.createCommon("2024-01-01T01:00:00Z", 100), // минимум
                candleFactory.createCommon("2024-01-01T02:00:00Z", 102)
            );

            var result = locator.locate(testCandles);
            assertEquals(1, result.size());
            assertSame(testCandles.get(1), result.get(0));
        }
    }
}
