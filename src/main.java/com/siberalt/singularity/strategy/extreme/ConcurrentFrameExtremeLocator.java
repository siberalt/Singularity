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

    private int extremeVicinity = DEFAULT_EXTREME_VICINITY;
    private final int frameSize;
    private final ExtremeLocator baseLocator;
    private int threadCount = Runtime.getRuntime().availableProcessors();
    private List<Candle> lastOverlapCandles = new ArrayList<>();
    private List<Candle> unfinishedFrameCandles = new ArrayList<>();
    private long globalStartFrameIndex = -1;

    public ConcurrentFrameExtremeLocator(int frameSize, ExtremeLocator baseLocator) {
        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
    }

    public ConcurrentFrameExtremeLocator(int frameSize, ExtremeLocator baseLocator, int threadCount) {
        this(frameSize, baseLocator, threadCount, DEFAULT_EXTREME_VICINITY);
    }

    public ConcurrentFrameExtremeLocator(
        int frameSize,
        ExtremeLocator baseLocator,
        int threadCount,
        int extremeVicinity
    ) {
        if (frameSize < 2) {
            throw new IllegalArgumentException("Frame size must be at least 2");
        }

        if (frameSize < extremeVicinity * 2) {
            throw new IllegalArgumentException("Frame size must be at least twice the extreme vicinity");
        }

        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
        this.threadCount = threadCount;
        this.extremeVicinity = extremeVicinity;
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        if (candles.isEmpty()) {
            return Collections.emptyList();
        }

        if (globalStartFrameIndex == -1) {
            globalStartFrameIndex = candles.get(0).getIndex();
        }

        if (!unfinishedFrameCandles.isEmpty()) {
            ArrayList<Candle> temp = new ArrayList<>(unfinishedFrameCandles);
            temp.addAll(candles);
            candles = temp;
        }

        List<Candle> extremeList = new ArrayList<>();

        boolean allFramesComplete = candles.size() % frameSize == 0;
        int totalFrames = (int) Math.ceil((double) candles.size() / frameSize) - (allFramesComplete ? 0 : 1);

        unfinishedFrameCandles = allFramesComplete
            ? new ArrayList<>()
            : new ArrayList<>(candles.subList(frameSize * totalFrames, candles.size()));

        // Thread pool with a fixed number of threads
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<List<Candle>>> futures = new ArrayList<>();

            for (int i = 0; i < totalFrames; i++) {
                int start = i * frameSize;
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

            if (!lastOverlapCandles.isEmpty()) {
                List<Candle> withOverlapCandles = new ArrayList<>(lastOverlapCandles);
                withOverlapCandles.addAll(candles);
                candles = withOverlapCandles;
            }

            extremeList = filterExtremes(
                extremeList,
                candles,
                executor,
                globalStartFrameIndex
            );

            int overlapStart = Math.max(0, candles.size() - extremeVicinity - (allFramesComplete ? 0 : 1));
            lastOverlapCandles = new ArrayList<>(candles.subList(overlapStart, candles.size()));
        }

        return extremeList;
    }

    /**
     * Фильтрует ложные экстремумы, проверяя их в контексте всего ряда свечей
     */
    private List<Candle> filterExtremes(
        List<Candle> candidateExtremes,
        List<Candle> allCandles,
        ExecutorService executorService,
        long globalStartIndex
    ) {
        Map<Long, Set<Candle>> frameEdgeExtremes = new HashMap<>();
        Set<Candle> extremeToRemove = new HashSet<>();

        for (Candle extreme : candidateExtremes) {
            long extremeIndex = extreme.getIndex() - globalStartIndex;
            if (extremeIndex == -1) continue;

            long endIndex = allCandles.get(allCandles.size() - 1).getIndex();

            if (isOutOfRange(extremeIndex, globalStartIndex, endIndex)) {
                extremeToRemove.add(extreme);
            } else if(!isWithinFrameBounds(extremeIndex, frameSize, extremeVicinity)) {
                long extremeFrameIndex = calculateFrameIndex(extremeIndex, frameSize);
                frameEdgeExtremes
                    .computeIfAbsent(extremeFrameIndex, k -> new HashSet<>())
                    .add(extreme);
            }
        }

        List<Future<Set<Candle>>> futures = new ArrayList<>();

        for (Map.Entry<Long, Set<Candle>> entry : frameEdgeExtremes.entrySet()) {
            long frameIndex = entry.getKey();

            long leftBound = Math.min(0, frameIndex * frameSize - extremeVicinity * 2L);
            long rightBound = Math.min(allCandles.size() - 1, (frameIndex + 1) * frameSize + extremeVicinity * 2L);
            List<Candle> neighborhood = allCandles.subList((int) leftBound, (int) (rightBound + 1));

            futures.add(executorService.submit(() -> {
                List<Candle> realExtremes = baseLocator.locate(neighborhood);
                Set<Candle> realExtremesSet = new HashSet<>(realExtremes); // Convert to Set for faster lookups
                return entry.getValue().removeAll(realExtremesSet) ? entry.getValue() : Collections.emptySet();
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
