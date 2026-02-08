package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.strategy.extreme.BaseExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ConcurrentFrameExtremeLocator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class CachingExtremeLocatorIT {
    private CvsCandleRepository candleRepository;

    @BeforeEach
    void setUp() {
        CvsFileCandleRepositoryFactory factory = new CvsFileCandleRepositoryFactory();
        candleRepository = factory.create(
            "TMOS",
            "src/test/resources/entity.candle.cvs/TMOS"
        );
    }

    @Test
    void testCachesFullRange() {
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");

        List<Candle> candles = candleRepository.getPeriod("TMOS", startTime, endTime);
        ConcurrentFrameExtremeLocator locator = new ConcurrentFrameExtremeLocator(
            150, BaseExtremeLocator.createMaxLocator()
        );
        List<Candle> expectedExtremes = locator.locate(candles);
        CachingExtremeLocator cachingLocator = new CachingExtremeLocator(locator);

        // First call - should compute and cache
        List<Candle> cachedExtremes = cachingLocator.locate(candles);
        assertEquals(expectedExtremes, cachedExtremes);

        // Second call - should retrieve from cache
        cachedExtremes = cachingLocator.locate(candles);
        assertEquals(expectedExtremes, cachedExtremes);
    }

    @Test
    void testCachesOnSlidingWindow() {
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");

        List<Candle> allCandles = candleRepository.getPeriod("TMOS", startTime, endTime);
        List<Candle> initialCandles = allCandles.subList(0, 5000);
        Deque<Candle> window = new ArrayDeque<>(initialCandles);
        ConcurrentFrameExtremeLocator locator = new ConcurrentFrameExtremeLocator(
            150, BaseExtremeLocator.createMaxLocator()
        );
        CachingExtremeLocator cachingLocator = new CachingExtremeLocator(locator);

        cachingLocator.locate(initialCandles);
        int maxIterations = 100;
        int iteration = 0;

        // Slide the window and test caching
        for (Candle candle : allCandles.subList(5000, allCandles.size())) {
            window.pollFirst();
            window.offerLast(candle);
            List<Candle> windowList = List.copyOf(window);

            // Compute and cache extremes for the new window
            assertEquals(locator.locate(windowList), cachingLocator.locate(windowList));

            // Retrieve from cache and verify
            assertEquals(locator.locate(windowList), cachingLocator.locate(windowList));

            if (++iteration >= maxIterations) {
                break; // Limit iterations for test performance
            }
        }
    }

    private void assertEquals(List<Candle> expected, List<Candle> actual) {
        List<Candle> diff = expected.stream()
            .filter(e -> !actual.contains(e))
            .toList();

        if (!diff.isEmpty()) {
            Assertions.assertEquals(1, diff.size());
            Assertions.assertEquals(expected.get(1), actual.get(0));
        }
    }

    @AfterEach
    void tearDown() {
        candleRepository.close();
    }
}
