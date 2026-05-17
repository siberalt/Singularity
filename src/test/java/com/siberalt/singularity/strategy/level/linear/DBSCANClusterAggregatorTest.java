package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.math.median.MedianCalculator;
import com.siberalt.singularity.math.median.RobustMedianCalculator;
import com.siberalt.singularity.shared.RangeDouble;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DBSCANClusterAggregatorTest {

    // Простая заглушка Candle
    private Candle candle(double price) {
        return Candle.of(TimePoint.NULL, price);
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

            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 2);
            List<Cluster> clusters = aggregator.aggregate(extremes, 1.0); // volatility = 1.0 → epsilon = 0.5

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

            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 3);
            List<Cluster> clusters = aggregator.aggregate(extremes, 1.0);

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

            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 2);

            List<Cluster> lowVol = aggregator.aggregate(extremes, 1.0); // epsilon = 0.5
            List<Cluster> highVol = aggregator.aggregate(extremes, 4.0); // epsilon = 2.0

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

            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 2);
            List<Cluster> clusters = aggregator.aggregate(extremes, 0.0); // epsilon = 0.0

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

            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 2);
            List<Cluster> clusters = aggregator.aggregate(extremes, 1.0);

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
            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 2);
            List<Cluster> clusters = aggregator.aggregate(null, 1.0);
            assertNotNull(clusters);
            assertTrue(clusters.isEmpty());
        }

        @Test
        @DisplayName("При пустом списке — возвращает пусто")
        void shouldReturnEmptyOnEmptyInput() {
            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 2);
            List<Cluster> clusters = aggregator.aggregate(Collections.emptyList(), 1.0);
            assertTrue(clusters.isEmpty());
        }

        @Test
        @DisplayName("Одна свеча — не формирует кластер")
        void singleCandleDoesNotFormCluster() {
            List<Candle> extremes = Collections.singletonList(candle(100.0));
            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 2);
            List<Cluster> clusters = aggregator.aggregate(extremes, 1.0);
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
            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 2, extractor, new RobustMedianCalculator());

            List<Cluster> clusters = aggregator.aggregate(extremes, 1.0);

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

            DBSCANClusterAggregator aggregator = new DBSCANClusterAggregator(0.5, 2, Candle::getClose, mockMedian);
            List<Candle> extremes = List.of(
                candle(100.0),
                candle(100.0)
            );
            List<Cluster> clusters = aggregator.aggregate(extremes, 1.0);

            assertEquals(1, clusters.size());
            assertEquals(999.9, clusters.get(0).price(), 0.001);
        }
    }
}
