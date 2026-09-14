package com.siberalt.singularity.strategy.analysis;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks of a signal the only question worth asking before building a strategy on it: does it predict
 * what happens next, and is what it predicts worth more than a round trip costs.
 * <p>
 * Answered off the candles alone, with no broker and no simulation, because a simulation answers a
 * different question - it mixes the signal with sizing, liquidity, costs and every assumption the
 * execution model makes, and when the result is bad there is no telling which of them was to blame.
 * This separates the one term that has to be positive before any of the rest can matter.
 * <p>
 * The measurement that does the real work is the pair of correlations. The signal at a bar is read
 * from that bar's close, so a position cannot be opened before the next bar's open; opening it at
 * this bar's open instead buys a move that has already happened. Reporting both makes the
 * difference visible, and it is usually most of the apparent signal.
 */
public class SignalPredictiveness {
    public static final int[] DEFAULT_HORIZONS = {1, 5, 15, 60};

    /** The window handed to the calculator, matching what a strategy would give it. */
    private int lookbackCandles = 60 * 24;

    /**
     * Evaluate every n-th bar. The estimates are averages over thousands of samples, so stepping
     * over most bars costs precision that rounds away while cutting the work by the same factor -
     * worth it for a calculator that reads its whole window on every call.
     */
    private int stride = 1;

    /** Signals at least this strong count towards the edge, matching a strategy's entry threshold. */
    private double signalThreshold = 0.9;

    private int[] horizons = DEFAULT_HORIZONS;

    public SignalPredictiveness setLookbackCandles(int lookbackCandles) {
        this.lookbackCandles = lookbackCandles;
        return this;
    }

    public SignalPredictiveness setStride(int stride) {
        if (stride < 1) {
            throw new IllegalArgumentException("Stride must be at least one bar, got " + stride);
        }

        this.stride = stride;
        return this;
    }

    public SignalPredictiveness setSignalThreshold(double signalThreshold) {
        this.signalThreshold = signalThreshold;
        return this;
    }

    public SignalPredictiveness setHorizons(int... horizons) {
        this.horizons = horizons;
        return this;
    }

    /**
     * @param candles    ordered oldest first, one instrument, one interval
     * @param calculator the signal under test, given the same shape of window a strategy gives it
     */
    public PredictivenessReport measure(List<Candle> candles, UpsideCalculator calculator) {
        int size = candles.size();
        int maxHorizon = 0;

        for (int horizon : horizons) {
            maxHorizon = Math.max(maxHorizon, horizon);
        }

        double[] signal = new double[size];
        long firedBars = 0;
        long evaluated = 0;

        for (int bar = lookbackCandles; bar + 1 + maxHorizon < size; bar += stride) {
            Upside upside = calculator.calculate(candles.subList(bar - lookbackCandles + 1, bar + 1));
            signal[bar] = upside.signal();
            evaluated++;

            if (upside.signal() != 0) {
                firedBars++;
            }
        }

        List<PredictivenessReport.HorizonStat> stats = new ArrayList<>();

        for (int horizon : horizons) {
            Correlation executable = new Correlation();
            Correlation sameBar = new Correlation();
            double baselineSum = 0;
            long baselineCount = 0;

            for (int bar = lookbackCandles; bar + 1 + horizon < size; bar += strideFor(horizon)) {
                // Every bar counts towards the baseline, including the ones the signal said
                // nothing about: the question it answers is what holding paid over this stretch.
                baselineSum += ratio(candles, bar + 1, bar + 1 + horizon);
                baselineCount++;

                if (signal[bar] == 0) {
                    continue;
                }

                executable.add(signal[bar], ratio(candles, bar + 1, bar + 1 + horizon));
                sameBar.add(signal[bar], ratio(candles, bar, bar + horizon));
            }

            stats.add(new PredictivenessReport.HorizonStat(
                horizon,
                executable.count(),
                executable.correlation(),
                sameBar.correlation(),
                executable.edgeBasisPoints(),
                executable.edgeCount(),
                executable.edgeStandardErrorBasisPoints(),
                baselineCount == 0 ? 0 : 10_000 * baselineSum / baselineCount
            ));
        }

        return new PredictivenessReport(evaluated, firedBars, lag1Autocorrelation(candles), stats);
    }

    /**
     * How far apart the bars counted towards one horizon sit: far enough that what each of them
     * measures does not overlap what the next one measures, and still on the grid of bars the signal
     * was evaluated on.
     * <p>
     * Overlapping stretches are not independent readings, and a noise floor worked out from how many
     * of them there are calls a signal real long before it is. At a horizon of sixty bars stepped one
     * at a time, sixty readings cover the same sixty bars of future and say about as much as one -
     * which is why everything this used to mark as standing outside the floor at the longer horizons
     * was inside it once counted honestly.
     * <p>
     * The price is precision: a horizon of sixty leaves a sixtieth of the samples, and the floor
     * widens to match. That is the floor that was always true; it was only ever the count that
     * flattered it.
     */
    protected int strideFor(int horizon) {
        return stride * Math.max(1, (horizon + stride - 1) / stride);
    }

    /** The return of holding from one bar's open to another's. */
    protected double ratio(List<Candle> candles, int from, int to) {
        double entry = candles.get(from).getOpenAsDouble();

        return entry == 0 ? 0 : candles.get(to).getOpenAsDouble() / entry - 1;
    }

    protected double lag1Autocorrelation(List<Candle> candles) {
        Correlation correlation = new Correlation();

        for (int bar = 2; bar < candles.size(); bar++) {
            double previous = candles.get(bar - 1).getCloseAsDouble();
            double beforeThat = candles.get(bar - 2).getCloseAsDouble();
            double current = candles.get(bar).getCloseAsDouble();

            if (beforeThat == 0 || previous == 0) {
                continue;
            }

            correlation.add(previous / beforeThat - 1, current / previous - 1);
        }

        return correlation.correlation();
    }

    /**
     * Pearson correlation and directional mean, both accumulated in one pass - the samples run to
     * hundreds of thousands and there is no reason to hold them.
     */
    protected class Correlation {
        private long count;
        private long edgeCount;
        private double sumX;
        private double sumY;
        private double sumXX;
        private double sumYY;
        private double sumXY;
        private double edgeSum;
        private double edgeSumSquares;

        public void add(double x, double y) {
            count++;
            sumX += x;
            sumY += y;
            sumXX += x * x;
            sumYY += y * y;
            sumXY += x * y;

            if (Math.abs(x) >= signalThreshold) {
                edgeCount++;
                edgeSum += Math.signum(x) * y;
                // The direction squares away, so this is the scatter of the returns themselves.
                edgeSumSquares += y * y;
            }
        }

        public long count() {
            return count;
        }

        public long edgeCount() {
            return edgeCount;
        }

        /**
         * How far the average trade could be from the truth by luck alone, in the same units as the
         * edge: the scatter of the trades divided by the root of how many there were.
         * <p>
         * Worth as much as the edge itself. Four hundred basis points a trade said nothing when it
         * turned out to be two trades, and nothing in the output said so until this did.
         */
        public double edgeStandardErrorBasisPoints() {
            if (edgeCount < 2) {
                return 0;
            }

            double mean = edgeSum / edgeCount;
            double variance = (edgeSumSquares - edgeCount * mean * mean) / (edgeCount - 1);

            return variance <= 0 ? 0 : 10_000 * Math.sqrt(variance / edgeCount);
        }

        public double correlation() {
            double denominator = Math.sqrt((count * sumXX - sumX * sumX) * (count * sumYY - sumY * sumY));

            return denominator == 0 ? 0 : (count * sumXY - sumX * sumY) / denominator;
        }

        public double edgeBasisPoints() {
            return edgeCount == 0 ? 0 : 10_000 * edgeSum / edgeCount;
        }
    }
}
