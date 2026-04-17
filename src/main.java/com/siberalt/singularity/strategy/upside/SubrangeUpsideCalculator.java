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

        return baseUpsideCalculator.calculate(lastCandles.subList(subRange.start(), subRange.end()));
    }

    public static SubrangeUpsideCalculator ofLastN(int n, UpsideCalculator baseUpsideCalculator) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        Function<List<Candle>, RangeInt> function = list -> {
            if (list == null || list.isEmpty()) {
                return RangeInt.EMPTY;
            }

            int size = list.size();
            int fromIndex = Math.max(0, size - n); // Защита от отрицательного индекса
            return new RangeInt(fromIndex, size);
        };

        return new SubrangeUpsideCalculator(function, baseUpsideCalculator);
    }

    public static SubrangeUpsideCalculator ofFirstN(int n, UpsideCalculator baseUpsideCalculator) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative");
        }
        Function<List<Candle>, RangeInt> function = list -> {
            if (list == null || list.isEmpty()) {
                return RangeInt.EMPTY;
            }
            int size = Math.min(n, list.size());
            return new RangeInt(0, size);
        };

        return new SubrangeUpsideCalculator(function, baseUpsideCalculator);
    }
}
