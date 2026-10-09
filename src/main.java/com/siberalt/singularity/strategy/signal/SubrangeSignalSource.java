package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.shared.RangeInt;

import java.util.List;
import java.util.function.Function;

public class SubrangeSignalSource implements SignalSource {
    private final Function<List<Candle>, RangeInt> rangeFunction;
    private final SignalSource baseSignalSource;

    public SubrangeSignalSource(
        Function<List<Candle>, RangeInt> rangeFunction,
        SignalSource baseSignalSource
    ) {
        this.rangeFunction = rangeFunction;
        this.baseSignalSource = baseSignalSource;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Signal.NEUTRAL;
        }

        RangeInt subRange = rangeFunction.apply(lastCandles);

        if (subRange.equals(RangeInt.EMPTY)) { // Check for empty or invalid ranges here!
            return Signal.NEUTRAL;
        }

        return baseSignalSource.calculate(lastCandles.subList(subRange.start(), subRange.end()));
    }

    public static SubrangeSignalSource ofLastN(int n, SignalSource baseSignalSource) {
        return ofLastN(n, baseSignalSource, false);
    }

    public static SubrangeSignalSource ofLastN(int n, SignalSource baseSignalSource, boolean allowPartialRange) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        Function<List<Candle>, RangeInt> function = list -> {
            if (list == null || list.isEmpty()) {
                return RangeInt.EMPTY;
            }

            int size = list.size();

            if (!allowPartialRange && size < n) {
                return RangeInt.EMPTY;
            }

            int fromIndex = Math.max(0, size - n); // Защита от отрицательного индекса
            return new RangeInt(fromIndex, size);
        };

        return new SubrangeSignalSource(function, baseSignalSource);
    }

    public static SubrangeSignalSource ofFirstN(int n, SignalSource baseSignalSource) {
        return ofFirstN(n, baseSignalSource, false);
    }

    public static SubrangeSignalSource ofFirstN(int n, SignalSource baseSignalSource, boolean allowPartialRange) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        Function<List<Candle>, RangeInt> function = list -> {
            if (list == null || list.isEmpty()) {
                return RangeInt.EMPTY;
            }

            int size = list.size();

            if (!allowPartialRange && size < n) {
                return RangeInt.EMPTY;
            }

            int toIndex = Math.min(n, list.size());
            return new RangeInt(0, toIndex);
        };

        return new SubrangeSignalSource(function, baseSignalSource);
    }
}
