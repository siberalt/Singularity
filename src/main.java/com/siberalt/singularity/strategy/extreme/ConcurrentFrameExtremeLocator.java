package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class ConcurrentFrameExtremeLocator implements ExtremeLocator {
    public static int DEFAULT_EXTREME_VICINITY = 2;
    public static int DEFAULT_START_INDEX = 0;
    public static int DEFAULT_FRAME_SIZE = 120;

    private int extremeVicinity = DEFAULT_EXTREME_VICINITY;
    private int frameSize = DEFAULT_FRAME_SIZE;
    private final ExtremeLocator baseLocator;
    private int threadCount = Runtime.getRuntime().availableProcessors();
    private long startIndex = DEFAULT_START_INDEX;

    public static class Builder {
        private long startIndex = DEFAULT_START_INDEX;
        private int frameSize = DEFAULT_FRAME_SIZE;
        private final ExtremeLocator baseLocator;
        private int threadCount = Runtime.getRuntime().availableProcessors();
        private int extremeVicinity = DEFAULT_EXTREME_VICINITY;

        public Builder(ExtremeLocator baseLocator) {
            if (baseLocator == null) {
                throw new IllegalArgumentException("Base locator cannot be null");
            }
            this.baseLocator = baseLocator;
        }

        public Builder setStartIndex(long startIndex) {
            if (startIndex < 0) {
                throw new IllegalArgumentException("Start index must be non-negative");
            }
            this.startIndex = startIndex;
            return this;
        }

        public Builder setFrameSize(int frameSize) {
            if (frameSize < 2) {
                throw new IllegalArgumentException("Frame size must be at least 2");
            }
            this.frameSize = frameSize;
            return this;
        }

        public Builder setThreadCount(int threadCount) {
            if (threadCount <= 0) {
                throw new IllegalArgumentException("Thread count must be greater than 0");
            }
            this.threadCount = threadCount;
            return this;
        }

        public Builder setExtremeVicinity(int extremeVicinity) {
            if (extremeVicinity < 0) {
                throw new IllegalArgumentException("Extreme vicinity must be non-negative");
            }
            this.extremeVicinity = extremeVicinity;
            return this;
        }

        public ConcurrentFrameExtremeLocator build() {
            return new ConcurrentFrameExtremeLocator(startIndex, frameSize, baseLocator, threadCount, extremeVicinity);
        }
    }

    public ConcurrentFrameExtremeLocator(ExtremeLocator baseLocator) {
        this.baseLocator = baseLocator;
    }

    public ConcurrentFrameExtremeLocator(int frameSize, ExtremeLocator baseLocator) {
        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
    }

    public ConcurrentFrameExtremeLocator(
        long startIndex,
        int frameSize,
        ExtremeLocator baseLocator,
        int threadCount,
        int extremeVicinity
    ) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("Start index must be non-negative");
        }

        if (frameSize < 2) {
            throw new IllegalArgumentException("Frame size must be at least 2");
        }

        if (frameSize < extremeVicinity * 2) {
            throw new IllegalArgumentException("Frame size must be at least twice the extreme vicinity");
        }

        this.startIndex = startIndex;
        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
        this.threadCount = threadCount;
        this.extremeVicinity = extremeVicinity;
    }

    public static Builder builder(ExtremeLocator baseLocator) {
        return new Builder(baseLocator);
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        if (candles.isEmpty()) {
            return Collections.emptyList();
        }

        long localStartIndex = candles.get(0).getIndex();
        int listIndexShift = (int) (Math.abs(localStartIndex - startIndex) % frameSize);
        int totalFrames = (candles.size() - listIndexShift) / frameSize;

        if (totalFrames <= 0) {
            return Collections.emptyList();
        }

        List<Candle> extremeList = new ArrayList<>();

        // Thread pool with a fixed number of threads
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<List<Candle>>> futures = new ArrayList<>();

            for (int i = 0; i < totalFrames; i++) {
                int start = i * frameSize + listIndexShift;
                int end = Math.min(start + frameSize, candles.size());

                List<Candle> frame = candles.subList(start, end);

                // Submit frame processing to the thread pool
                futures.add(executor.submit(() -> baseLocator.locate(frame)));
            }

            // Collect results timeFrom all threads
            for (Future<List<Candle>> future : futures) {
                try {
                    extremeList.addAll(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    executor.shutdownNow(); // Прерываем все задачи при ошибке
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Ошибка обработки фрейма", e);
                }
            }

            return filterExtremes(
                extremeList,
                candles.subList(listIndexShift, totalFrames * frameSize + listIndexShift),
                executor
            );
        }
    }

    /**
     * Фильтрует ложные экстремумы, проверяя их в контексте всего ряда свечей
     */
    private List<Candle> filterExtremes(
        List<Candle> candidateExtremes,
        List<Candle> allCandles,
        ExecutorService executorService
    ) {
        Map<Long, Set<Candle>> frameEdgeExtremes = new HashMap<>();
        Set<Candle> extremeToRemove = new HashSet<>();

        long startIndex = allCandles.get(0).getIndex();
        long endIndex = allCandles.get(allCandles.size() - 1).getIndex();

        for (Candle extreme : candidateExtremes) {
            if (isOutOfRange(extreme.getIndex(), startIndex, endIndex)) {
                extremeToRemove.add(extreme);
                continue;
            }

            long extremeIndex = extreme.getIndex() - startIndex;

            if (!isWithinFrameBounds(extremeIndex, frameSize, extremeVicinity)) {
                long extremeFrameIndex = calculateFrameIndex(extremeIndex, frameSize);
                frameEdgeExtremes
                    .computeIfAbsent(extremeFrameIndex, k -> new HashSet<>())
                    .add(extreme);
            }
        }

        List<Future<Set<Candle>>> futures = new ArrayList<>();

        for (Map.Entry<Long, Set<Candle>> entry : frameEdgeExtremes.entrySet()) {
            long frameIndex = entry.getKey();

            long leftBound = Math.max(0, frameIndex * frameSize - extremeVicinity * 2L);
            long rightBound = Math.min(allCandles.size() - 1, frameIndex * frameSize + extremeVicinity * 2L);
            List<Candle> neighborhood = allCandles.subList((int) leftBound, (int) rightBound);

            futures.add(executorService.submit(() -> {
                List<Candle> realExtremes = baseLocator.locate(neighborhood);
                Set<Candle> realExtremesSet = new HashSet<>(realExtremes); // Convert to Set for faster lookups
                entry.getValue().removeAll(realExtremesSet);

                return entry.getValue();
            }));
        }

        for (Future<Set<Candle>> future : futures) {
            try {
                extremeToRemove.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                executorService.shutdownNow(); // Прерываем все задачи при ошибке
                Thread.currentThread().interrupt();
                throw new RuntimeException("Ошибка фильтрации экстремумов", e);
            }
        }

        return candidateExtremes.stream()
            .filter(candidate -> !extremeToRemove.contains(candidate))
            .distinct()
            .collect(Collectors.toList());
    }

    private boolean isOutOfRange(long extremeIndex, long startIndex, long endIndex) {
        return extremeIndex - startIndex < extremeVicinity || endIndex - extremeIndex < extremeVicinity;
    }

    private boolean isWithinFrameBounds(long extremeIndex, int frameSize, int overlapSize) {
        long framePosition = extremeIndex % frameSize;
        return framePosition >= overlapSize && frameSize - framePosition >= overlapSize;
    }

    private long calculateFrameIndex(long extremeIndex, int frameSize) {
        return (extremeIndex + frameSize / 2) / frameSize;
    }
}
