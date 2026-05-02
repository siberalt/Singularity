package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.shared.RangeInt;

import java.util.List;
import java.util.function.Function;

public class SubrangeUpsideCalculator implements UpsideCalculator {
    private final Function<List<Candle>, RangeInt> rangeFunction;
    private final UpsideCalculator baseUpsideCalculator;

    public SubrangeUpsideCalculator(
        Function<List<Candle>, RangeInt> rangeFunction,
        UpsideCalculator baseUpsideCalculator
    ) {
        this.rangeFunction = rangeFunction;
        this.baseUpsideCalculator = baseUpsideCalculator;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        RangeInt subRange = rangeFunction.apply(lastCandles);

        if (subRange.equals(RangeInt.EMPTY)) { // Check for empty or invalid ranges here!
            return Upside.NEUTRAL;
        }

        return baseUpsideCalculator.calculate(lastCandles.subList(subRange.start(), subRange.end()));
    }

    public static SubrangeUpsideCalculator ofLastN(int n, UpsideCalculator baseUpsideCalculator) {
        return ofLastN(n, baseUpsideCalculator, false);
    }

    public static SubrangeUpsideCalculator ofLastN(int n, UpsideCalculator baseUpsideCalculator, boolean allowPartialRange) {
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

        return new SubrangeUpsideCalculator(function, baseUpsideCalculator);
    }

    public static SubrangeUpsideCalculator ofFirstN(int n, UpsideCalculator baseUpsideCalculator) {
        return ofFirstN(n, baseUpsideCalculator, false);
    }

    public static SubrangeUpsideCalculator ofFirstN(int n, UpsideCalculator baseUpsideCalculator, boolean allowPartialRange) {
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

        return new SubrangeUpsideCalculator(function, baseUpsideCalculator);
    }
}
