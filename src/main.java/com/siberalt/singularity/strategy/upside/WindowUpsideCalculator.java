package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.ArrayDeque;
import java.util.function.Function;

public class WindowUpsideCalculator implements UpsideCalculator {
    private static final Function<List<Candle>, List<Candle>> DEFAULT_WINDOW_INIT_FUNCTION = candles -> Collections.emptyList();

    private final UpsideCalculator baseCalculator;
    private final int windowSize;
    private Deque<Candle> window;
    private Function<List<Candle>, List<Candle>> windowInitFunction = DEFAULT_WINDOW_INIT_FUNCTION;
    private boolean isInitialized = false;

    public WindowUpsideCalculator(
        UpsideCalculator baseCalculator,
        int windowSize, Function<List<Candle>,
        List<Candle>> windowInitFunction
    ) {
        this(baseCalculator, windowSize);
        this.windowInitFunction = windowInitFunction;
    }

    public WindowUpsideCalculator(UpsideCalculator baseCalculator, int windowSize) {
        if (baseCalculator == null) {
            throw new IllegalArgumentException("Base calculator cannot be null");
        }
        if (windowSize < 1) {
            throw new IllegalArgumentException("Window size must be at least 1");
        }
        this.baseCalculator = baseCalculator;
        this.windowSize = windowSize;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (!isInitialized) {
            List<Candle> initialWindow = windowInitFunction.apply(lastCandles);
            window = new ArrayDeque<>(initialWindow);
            isInitialized = true;
        }
        // Add the new candles to the window
        window.addAll(lastCandles);

        // Remove excess candles if the window size exceeds the limit
        while (window.size() > windowSize) {
            window.pollFirst();
        }

        return baseCalculator.calculate(window.stream().toList());
    }
}
