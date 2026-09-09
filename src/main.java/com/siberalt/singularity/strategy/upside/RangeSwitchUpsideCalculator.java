package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Measures something about the market and hands the question to whichever calculator that reading
 * belongs to.
 * <p>
 * What it exists for: the same signal is right on one instrument and backwards on another. A trend
 * read on a share whose moves carry on earns; on one whose moves come back the same read inverted
 * earns, and the read itself loses. Which of the two an instrument is doing is measurable without
 * reference to any signal - see
 * {@link com.siberalt.singularity.strategy.analysis.VarianceRatio} - so it can be asked directly
 * rather than discovered by trying both and keeping whichever did better.
 * <p>
 * That distinction is the whole point. Handing a walk-forward both directions as candidates does
 * find the right one where it matters, but it also finds it where it does not: on a share that
 * merely wanders, the wrong direction won the selection in half the folds, and the cost of the
 * extra choices showed up as a third more hindsight in the result. A coefficient measured from the
 * data settles the question without spending a degree of freedom on it.
 * <p>
 * Ranges are half-open, {@code [from, to)}, and the first one containing the reading wins - so
 * overlapping ranges are allowed and resolved by the order they were given. A reading belonging to
 * none of them is an answer of {@link Upside#NEUTRAL}: the market is in a state nothing here was
 * written for, and saying nothing is the honest response.
 */
public class RangeSwitchUpsideCalculator implements UpsideCalculator {
    /**
     * Whatever the choice is made on, measured from the same candles the calculators see. It has to
     * be something the market is doing rather than something a signal thinks, or this is only a
     * more roundabout way of picking by signal strength.
     */
    @FunctionalInterface
    public interface Coefficient {
        double of(List<Candle> lastCandles);
    }

    /**
     * @param from inclusive
     * @param to   exclusive
     */
    public record Branch(double from, double to, UpsideCalculator calculator) {
        public Branch {
            if (calculator == null) {
                throw new IllegalArgumentException("A branch needs something to delegate to");
            }

            if (from > to) {
                throw new IllegalArgumentException("A branch runs from lower to higher, got " + from + ".." + to);
            }
        }

        public boolean contains(double reading) {
            return reading >= from && reading < to;
        }

        public static Branch below(double to, UpsideCalculator calculator) {
            return new Branch(Double.NEGATIVE_INFINITY, to, calculator);
        }

        public static Branch from(double from, UpsideCalculator calculator) {
            return new Branch(from, Double.POSITIVE_INFINITY, calculator);
        }
    }

    private final Coefficient coefficient;
    private final List<Branch> branches;

    public RangeSwitchUpsideCalculator(Coefficient coefficient, List<Branch> branches) {
        if (coefficient == null) {
            throw new IllegalArgumentException("Nothing to switch on");
        }

        if (branches == null || branches.isEmpty()) {
            throw new IllegalArgumentException("Nothing to switch between");
        }

        this.coefficient = coefficient;
        this.branches = List.copyOf(branches);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        double reading = coefficient.of(lastCandles);

        for (Branch branch : branches) {
            if (branch.contains(reading)) {
                return branch.calculator().calculate(lastCandles);
            }
        }

        return Upside.NEUTRAL;
    }
}
