package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Rescales another calculator's signal against how strong that calculator's own readings usually
 * are, so that a threshold means the same thing whatever is underneath it.
 * <p>
 * A strategy compares the signal to a fixed number - buy above nine tenths - and every calculator
 * produces its own spread of values. A raw order-flow imbalance averaged over thirty bars barely
 * leaves a tenth; an average of two signals that agree only loosely reaches nine tenths only when
 * both are near their extremes at once, which is almost never. Both cases came up here, and in both
 * the strategy simply stopped trading, which reads as a signal that finds nothing rather than a
 * threshold that cannot be reached.
 * <p>
 * After this, one means as strong as this signal usually gets. The threshold then says how unusual
 * a reading has to be before it is worth acting on, which is what a threshold ought to say, and the
 * same number carries over to a different signal or a different instrument.
 * <p>
 * Zero passes through untouched. A calculator that declines to answer - the slope below its
 * goodness-of-fit gate, the flow with no split recorded - is saying it has no opinion, and scaling
 * that up would manufacture one.
 */
public class NormalizedUpsideCalculator implements UpsideCalculator {
    /** Readings kept. About a month of hourly bars: long enough to be a distribution, short enough to follow a market that changes. */
    public static final int DEFAULT_MEMORY = 500;

    /** Readings needed before an answer is given at all. */
    public static final int DEFAULT_WARMUP = 50;

    private final UpsideCalculator delegate;
    private final Deque<Double> readings = new ArrayDeque<>();
    private long seenReadings;
    private int memory = DEFAULT_MEMORY;
    private int warmup = DEFAULT_WARMUP;

    public NormalizedUpsideCalculator(UpsideCalculator delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("Nothing to normalize");
        }

        this.delegate = delegate;
    }

    public NormalizedUpsideCalculator setMemory(int memory) {
        if (memory < 1) {
            throw new IllegalArgumentException("Memory must be at least one reading, got " + memory);
        }

        this.memory = memory;
        return this;
    }

    public NormalizedUpsideCalculator setWarmup(int warmup) {
        if (warmup < 1) {
            throw new IllegalArgumentException("Warmup must be at least one reading, got " + warmup);
        }

        this.warmup = warmup;
        return this;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        Upside upside = delegate.calculate(lastCandles);

        if (upside.signal() == 0) {
            return upside;
        }

        // Measured before this reading joins them, so a signal is never scaled by itself.
        double scale = typicalMagnitude();
        // Counted apart from what is remembered: the memory is a window and may well be shorter
        // than the warmup, and a size capped by it would then never reach one.
        long seen = seenReadings;

        remember(Math.abs(upside.signal()));

        if (seen < warmup || scale <= 0) {
            return Upside.NEUTRAL;
        }

        return new Upside(Math.tanh(upside.signal() / scale), upside.strength());
    }

    /**
     * The average strength of the readings so far, ignoring the ones where the calculator said
     * nothing - those are absences of opinion, and counting them as weak opinions would shrink the
     * scale and make every real reading look extreme.
     */
    protected double typicalMagnitude() {
        if (readings.isEmpty()) {
            return 0;
        }

        double sum = 0;

        for (double reading : readings) {
            sum += reading;
        }

        return sum / readings.size();
    }

    protected void remember(double magnitude) {
        seenReadings++;
        readings.addLast(magnitude);

        while (readings.size() > memory) {
            readings.pollFirst();
        }
    }
}
