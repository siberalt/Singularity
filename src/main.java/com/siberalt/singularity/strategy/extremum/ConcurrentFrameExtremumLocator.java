package com.siberalt.singularity.strategy.extremum;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ConcurrentFrameExtremumLocator implements ExtremumLocator {
    private final int frameSize;
    private final ExtremumLocator baseLocator;
    private int threadCount = Runtime.getRuntime().availableProcessors();

    public ConcurrentFrameExtremumLocator(int frameSize, ExtremumLocator baseLocator) {
        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
    }

    public ConcurrentFrameExtremumLocator(int frameSize, ExtremumLocator baseLocator, int threadCount) {
        this.frameSize = frameSize;
        this.baseLocator = baseLocator;
        this.threadCount = threadCount;
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        List<Candle> extremumList = new ArrayList<>();
        int totalFrames = (int) Math.ceil((double) candles.size() / frameSize);

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
                    extremumList.addAll(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    executor.shutdownNow(); // Прерываем все задачи при ошибке
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Ошибка обработки фрейма", e);
                }
            }
        }

        return extremumList;
    }
}
