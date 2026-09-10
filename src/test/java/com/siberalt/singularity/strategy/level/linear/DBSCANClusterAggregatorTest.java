package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.math.median.MedianCalculator;
import com.siberalt.singularity.shared.RangeDouble;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DBSCANClusterAggregatorTest {
    private final CandleFactory candleFactory = new CandleFactory("TMOS");
    private final ExtremeLocator extremeLocator = mock(ExtremeLocator.class);
    private final VolatilityCalculator volatilityCalculator = mock(VolatilityCalculator.class);

    private DBSCANClusterAggregator createDefaultAggregator() {
        return createAggregatorBuilderWithMocks().build();
    }

    // Простая заглушка Candle
    private Candle candle(double price) {
        return candleFactory.createCommon(price);
    }

    private DBSCANClusterAggregator.Builder createAggregatorBuilderWithMocks() {
        return DBSCANClusterAggregator.builder()
            .volatilityCalculator(volatilityCalculator)
            .extremeLocator(extremeLocator);
    }

    @Nested
    @DisplayName("Базовые кейсы")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class BasicCases {

        @Test
        @DisplayName("Должен сформировать кластер из близких точек")
        void shouldFormClusterFromClosePoints() {
            List<Candle> extremes = Arrays.asList(
                candle(100.0),
                candle(100.1),
                candle(99.9),
                candle(105.0) // выброс
            );

            when(extremeLocator.locate(anyList())).thenReturn(extremes);
            when(volatilityCalculator.calculate(anyList())).thenReturn(0.2);

            DBSCANClusterAggregator aggregator = createAggregatorBuilderWithMocks()
                .multiplier(0.5)
                .minPoints(2)
                .build();

            List<Cluster> clusters = aggregator.aggregate(extremes); // volatility = 1.0 → epsilon = 0.5

            assertEquals(1, clusters.size(), "Ожидается один кластер");
            Cluster cluster = clusters.get(0);
            assertEquals(3, cluster.extremes().size(), "Кластер должен содержать 3 близкие свечи");
            assertTrue(cluster.priceRange().contains(100.0));
            assertTrue(cluster.priceRange().contains(99.9));
            assertTrue(cluster.priceRange().contains(100.1));
            assertFalse(cluster.priceRange().contains(105.0));
        }

        @Test
        @DisplayName("Не должен формировать кластер, если точек меньше minPoints")
        void shouldNotFormClusterIfLessThanMinPoints() {
            List<Candle> extremes = Arrays.asList(
                candle(100.0),
                candle(100.1)
            );

            when(extremeLocator.locate(anyList())).thenReturn(extremes);

            DBSCANClusterAggregator aggregator = createAggregatorBuilderWithMocks()
                .multiplier(0.5)
                .minPoints(3)
                .build();
            List<Cluster> clusters = aggregator.aggregate(extremes);

            assertTrue(clusters.isEmpty(), "Кластер не должен быть создан при minPoints=3");
        }
    }

    @Nested
    @DisplayName("Зависимость от волатильности")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class VolatilityDependence {

        @Test
        @DisplayName("При высокой волатильности — больший epsilon → больше кластеров")
        void shouldCreateLargerClustersWithHighVolatility() {
            List<Candle> extremes = Arrays.asList(
                candle(100.0),
                candle(101.0),
                candle(102.0)
            );

            DBSCANClusterAggregator aggregator = createAggregatorBuilderWithMocks()
                .multiplier(0.5)
                .minPoints(2)
                .build();

            when(extremeLocator.locate(anyList())).thenReturn(extremes);
            List<Cluster> lowVol = aggregator.aggregate(extremes); // epsilon = 0.5

            when(volatilityCalculator.calculate(anyList())).thenReturn(2.0);
            List<Cluster> highVol = aggregator.aggregate(extremes); // epsilon = 2.0

            assertTrue(lowVol.isEmpty(), "При низкой волатильности точки слишком далеко");
            assertEquals(1, highVol.size(), "При высокой волатильности — кластер формируется");
        }

        @Test
        @DisplayName("При нулевой волатильности — кластеры не формируются")
        void shouldNotFormClustersWithZeroVolatility() {
            List<Candle> extremes = Arrays.asList(
                candle(100.0),
                candle(100.1)
            );

            DBSCANClusterAggregator aggregator = createAggregatorBuilderWithMocks()
                .multiplier(0.5)
                .minPoints(2)
                .build();

            when(extremeLocator.locate(anyList())).thenReturn(extremes);
            when(volatilityCalculator.calculate(anyList())).thenReturn(0.0);

            List<Cluster> clusters = aggregator.aggregate(extremes); // epsilon = 0.0

            assertTrue(clusters.isEmpty());
        }
    }

    @Nested
    @DisplayName("Шум и выбросы")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class NoiseAndOutliers {

        @Test
        @DisplayName("Выбросы должны игнорироваться (не входить в кластеры)")
        void outliersShouldBeIgnored() {
            List<Candle> extremes = Arrays.asList(
                candle(100.0),
                candle(100.1),
                candle(105.0), // далеко
                candle(110.0)  // ещё дальше
            );

            DBSCANClusterAggregator aggregator = createAggregatorBuilderWithMocks()
                .multiplier(0.5)
                .minPoints(2)
                .build();

            when(extremeLocator.locate(anyList())).thenReturn(extremes);
            when(volatilityCalculator.calculate(anyList())).thenReturn(0.2);

            List<Cluster> clusters = aggregator.aggregate(extremes);

            assertEquals(1, clusters.size());
            Set<Candle> clusterCandles = clusters.get(0).extremes();
            assertTrue(clusterCandles.contains(extremes.get(0)));
            assertTrue(clusterCandles.contains(extremes.get(1)));
            assertFalse(clusterCandles.contains(extremes.get(2)));
            assertFalse(clusterCandles.contains(extremes.get(3)));
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCases {

        @Test
        @DisplayName("При null списке — возвращает пусто")
        void shouldReturnEmptyOnNullInput() {
            DBSCANClusterAggregator aggregator = createDefaultAggregator();
            List<Cluster> clusters = aggregator.aggregate(null);
            assertNotNull(clusters);
            assertTrue(clusters.isEmpty());
        }

        @Test
        @DisplayName("При пустом списке — возвращает пусто")
        void shouldReturnEmptyOnEmptyInput() {
            DBSCANClusterAggregator aggregator = createDefaultAggregator();
            List<Cluster> clusters = aggregator.aggregate(Collections.emptyList());
            assertTrue(clusters.isEmpty());
        }

        @Test
        @DisplayName("Одна свеча — не формирует кластер")
        void singleCandleDoesNotFormCluster() {
            List<Candle> extremes = Collections.singletonList(candle(100.0));
            DBSCANClusterAggregator aggregator = createDefaultAggregator();
            List<Cluster> clusters = aggregator.aggregate(extremes);
            assertTrue(clusters.isEmpty());
        }
    }

    @Nested
    @DisplayName("Кастомные компоненты")
    class CustomComponents {

        @Test
        @DisplayName("Поддерживает кастомный PriceExtractor")
        void shouldWorkWithCustomPriceExtractor() {
            List<Candle> extremes = Arrays.asList(
                candle(100.0),
                candle(100.1)
            );

            PriceExtractor extractor = c -> Quotation.of(String.valueOf(c.getCloseAsDouble() + 10.0));

            DBSCANClusterAggregator aggregator = createAggregatorBuilderWithMocks()
                .multiplier(0.5)
                .minPoints(2)
                .priceExtractor(extractor)
                .build();

            when(extremeLocator.locate(anyList())).thenReturn(extremes);
            when(volatilityCalculator.calculate(anyList())).thenReturn(0.2);

            List<Cluster> clusters = aggregator.aggregate(extremes);

            assertEquals(1, clusters.size());
            RangeDouble range = clusters.get(0).priceRange();
            assertTrue(range.contains(110.0));
            assertTrue(range.contains(110.1));
        }

        @Test
        @DisplayName("Поддерживает кастомный MedianCalculator")
        void shouldWorkWithCustomMedianCalculator() {
            MedianCalculator mockMedian = mock(MedianCalculator.class);
            when(mockMedian.calculateMedian(any())).thenReturn(999.9);

            VolatilityCalculator mockVolatility = mock(VolatilityCalculator.class);
            when(mockVolatility.calculate(any())).thenReturn(1.0);

            ExtremeLocator extremeLocator = mock(ExtremeLocator.class);

            List<Candle> extremes = List.of(
                candle(100.0),
                candle(100.0)
            );
            when(extremeLocator.locate(anyList())).thenReturn(extremes);

            DBSCANClusterAggregator aggregator = DBSCANClusterAggregator.builder()
                .multiplier(0.5)
                .minPoints(2)
                .localVolatilityWindow(7)
                .extremeLocator(extremeLocator)
                .volatilityCalculator(mockVolatility)
                .priceExtractor(Candle::close)
                .medianCalculator(mockMedian)
                .build();

            List<Cluster> clusters = aggregator.aggregate(extremes);

            assertEquals(1, clusters.size());
            assertEquals(999.9, clusters.get(0).price(), 0.001);
        }
    }

    @Nested
    @DisplayName("Локальная волатильность")
    class LocalVolatility {

        /**
         * Candles rolled up to a wider interval keep the index of the bar they opened on, so their
         * neighbours are many indexes apart rather than one. Taking an index for a position then
         * runs off the end of the window, which is what used to throw.
         */
        @Test
        @DisplayName("Свечи с разреженными индексами не выводят окно за границы")
        void handlesCandlesWhoseIndexesAreFarApart() {
            List<Candle> candles = new ArrayList<>();

            for (int hour = 0; hour < 10; hour++) {
                // One candle per hour, indexes sixty apart as an hourly bar built of minutes is.
                for (int minute = 0; minute < 60; minute++) {
                    candleFactory.createCommon(100.0 + hour);
                }

                candles.add(candleFactory.createCommon(100.0 + hour));
            }

            when(extremeLocator.locate(candles)).thenReturn(List.of(candles.getFirst(), candles.getLast()));
            when(volatilityCalculator.calculate(any())).thenReturn(1.0);

            DBSCANClusterAggregator aggregator = createAggregatorBuilderWithMocks()
                .multiplier(100)
                .minPoints(2)
                .localVolatilityWindow(2)
                .build();

            assertDoesNotThrow(() -> aggregator.aggregate(candles));
        }

        @Test
        @DisplayName("Разные экстремумы получают разную локальную волатильность")
        void differentExtremesGetDifferentLocalVolatilities() {
            List<Candle> candles = Arrays.asList(
                candle(100.0),
                candle(101.0),
                candle(102.0),
                candle(105.0),
                candle(100.5)
            );

            List<Candle> extremes = Arrays.asList(candles.getFirst(), candles.getLast());

            when(volatilityCalculator.calculate(candles.subList(0, 2))).thenReturn(0.7);
            when(volatilityCalculator.calculate(candles.subList(3, 5))).thenReturn(0.5);
            when(extremeLocator.locate(candles)).thenReturn(extremes);

            DBSCANClusterAggregator aggregator = createAggregatorBuilderWithMocks()
                .multiplier(1)
                .minPoints(2)
                .localVolatilityWindow(1)
                .build();

            List<Cluster> clusters = aggregator.aggregate(candles);

            assertEquals(1, clusters.size());

            verify(volatilityCalculator).calculate(candles.subList(0, 2));
            verify(volatilityCalculator).calculate(candles.subList(3, 5));
        }

        @Test
        @DisplayName("Разная волатильность влияет на размеры кластеров")
        void differentVolatilityAffectsClusterSizes() {
            List<Candle> candles = Arrays.asList(
                candle(100.0),
                candle(100.1),
                candle(100.2),
                candle(150.0),
                candle(150.1),
                candle(150.2)
            );

            when(extremeLocator.locate(candles)).thenReturn(candles);
            when(volatilityCalculator.calculate(candles.subList(0, 2))).thenReturn(0.2);
            when(volatilityCalculator.calculate(candles.subList(0, 3))).thenReturn(0.2);
            when(volatilityCalculator.calculate(candles.subList(1, 4))).thenReturn(0.2);
            when(volatilityCalculator.calculate(candles.subList(2, 5))).thenReturn(0.7);
            when(volatilityCalculator.calculate(candles.subList(3, 6))).thenReturn(0.7);
            when(volatilityCalculator.calculate(candles.subList(4, 6))).thenReturn(0.7);

            DBSCANClusterAggregator aggregator = createAggregatorBuilderWithMocks()
                .multiplier(1)
                .minPoints(2)
                .localVolatilityWindow(1)
                .build();

            List<Cluster> clusters = aggregator.aggregate(candles);

            assertEquals(2, clusters.size(), "Должно быть два кластера - один для каждой группы");
            assertEquals(3, clusters.get(0).size());
            assertEquals(3, clusters.get(1).size());

            verify(volatilityCalculator).calculate(candles.subList(0, 2));
            verify(volatilityCalculator).calculate(candles.subList(0, 3));
            verify(volatilityCalculator).calculate(candles.subList(1, 4));
            verify(volatilityCalculator).calculate(candles.subList(2, 5));
            verify(volatilityCalculator).calculate(candles.subList(3, 6));
            verify(volatilityCalculator).calculate(candles.subList(4, 6));
        }
    }
}
