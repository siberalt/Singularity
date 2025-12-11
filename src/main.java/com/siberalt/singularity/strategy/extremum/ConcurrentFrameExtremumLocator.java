package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ConcurrentFrameExtremumLocator implements ExtremumLocator {
    public static int DEFAULT_EXTREMUM_VICINITY = 2;

    private int extremumVicinity = DEFAULT_EXTREMUM_VICINITY;
    private final int frameSize;
    private final ExtremumLocator baseLocator;
    private int threadCount = Runtime.getRuntime().availableProcessors();
    private List<Candle> lastOverlapCandles = new ArrayList<>();
    private List<Candle> unfinishedFrameCandles = new ArrayList<>();
    private long globalStartFrameIndex = -1;

    public ConcurrentFrameExtremumLocator(int frameSize, ExtremumLocator baseLocator) {
        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
    }

    public ConcurrentFrameExtremumLocator(int frameSize, ExtremumLocator baseLocator, int threadCount) {
        this(frameSize, baseLocator, threadCount, DEFAULT_EXTREMUM_VICINITY);
    }

    public ConcurrentFrameExtremumLocator(
        int frameSize,
        ExtremumLocator baseLocator,
        int threadCount,
        int extremumVicinity
    ) {
        if (frameSize < 2) {
            throw new IllegalArgumentException("Frame size must be at least 2");
        }

        if (frameSize < extremumVicinity * 2) {
            throw new IllegalArgumentException("Frame size must be at least twice the extremum vicinity");
        }

        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
        this.threadCount = threadCount;
        this.extremumVicinity = extremumVicinity;
    }

    @Override
    public List<Candle> locate(List<Candle> candles, CandleIndexProvider candleIndexProvider) {
        if (candles.isEmpty()) {
            return Collections.emptyList();
        }

        if (globalStartFrameIndex == -1) {
            globalStartFrameIndex = candleIndexProvider.provideIndex(candles.get(0));
        }

        if (!unfinishedFrameCandles.isEmpty()) {
            ArrayList<Candle> temp = new ArrayList<>(unfinishedFrameCandles);
            temp.addAll(candles);
            candles = temp;
        }

        List<Candle> extremumList = new ArrayList<>();

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
                futures.add(executor.submit(() -> baseLocator.locate(frame, candleIndexProvider)));
            }

            // Collect results timeFrom all threads
            for (Future<List<Candle>> future : futures) {
                try {
                    extremumList.addAll(future.get());
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

            extremumList = filterExtremums(
                extremumList,
                candles,
                candleIndexProvider,
                executor,
                globalStartFrameIndex
            );

            int overlapStart = Math.max(0, candles.size() - extremumVicinity - (allFramesComplete ? 0 : 1));
            lastOverlapCandles = new ArrayList<>(candles.subList(overlapStart, candles.size()));
        }

        return extremumList;
    }

    /**
     * Фильтрует ложные экстремумы, проверяя их в контексте всего ряда свечей
     */
    private List<Candle> filterExtremums(
        List<Candle> candidateExtremums,
        List<Candle> allCandles,
        CandleIndexProvider candleIndexProvider,
        ExecutorService executorService,
        long globalStartIndex
    ) {
        Map<Long, Set<Candle>> frameEdgeExtremums = new HashMap<>();
        Set<Candle> extremumsToRemove = new HashSet<>();

        for (Candle extremum : candidateExtremums) {
            long extremumIndex = candleIndexProvider.provideIndex(extremum) - globalStartIndex;
            if (extremumIndex == -1) continue;

            long endIndex = candleIndexProvider.provideIndex(allCandles.get(allCandles.size() - 1));

            if (isOutOfRange(extremumIndex, globalStartIndex, endIndex)) {
                extremumsToRemove.add(extremum);
            } else if(!isWithinFrameBounds(extremumIndex, frameSize, extremumVicinity)) {
                long extremumFrameIndex = calculateFrameIndex(extremumIndex, frameSize);
                frameEdgeExtremums
                    .computeIfAbsent(extremumFrameIndex, k -> new HashSet<>())
                    .add(extremum);
            }
        }

        List<Future<Set<Candle>>> futures = new ArrayList<>();

        for (Map.Entry<Long, Set<Candle>> entry : frameEdgeExtremums.entrySet()) {
            long frameIndex = entry.getKey();

            long leftBound = Math.min(0, frameIndex * frameSize - extremumVicinity * 2L);
            long rightBound = Math.min(allCandles.size() - 1, (frameIndex + 1) * frameSize + extremumVicinity * 2L);
            List<Candle> neighborhood = allCandles.subList((int) leftBound, (int) (rightBound + 1));

            futures.add(executorService.submit(() -> {
                List<Candle> realExtremums = baseLocator.locate(neighborhood, candleIndexProvider);
                Set<Candle> realExtremumsSet = new HashSet<>(realExtremums); // Convert to Set for faster lookups
                return entry.getValue().removeAll(realExtremumsSet) ? entry.getValue() : Collections.emptySet();
            }));
        }

        for (Future<Set<Candle>> future : futures) {
            try {
                extremumsToRemove.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                executorService.shutdownNow(); // Прерываем все задачи при ошибке
                Thread.currentThread().interrupt();
                throw new RuntimeException("Ошибка фильтрации экстремумов", e);
            }
        }

        return candidateExtremums.stream()
            .filter(candidate -> !extremumsToRemove.contains(candidate))
            .distinct()
            .collect(Collectors.toList());
    }

    private boolean isOutOfRange(long extremumIndex, long startIndex, long endIndex) {
        return extremumIndex - startIndex < extremumVicinity || endIndex - extremumIndex < extremumVicinity;
    }

    private boolean isWithinFrameBounds(long extremumIndex, int frameSize, int overlapSize) {
        long framePosition = extremumIndex % frameSize;
        return framePosition >= overlapSize && frameSize - framePosition >= overlapSize;
    }

    private long calculateFrameIndex(long extremumIndex, int frameSize) {
        return (extremumIndex + frameSize / 2) / frameSize;
    }
}
