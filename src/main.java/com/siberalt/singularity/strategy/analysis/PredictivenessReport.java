package com.siberalt.singularity.strategy.analysis;

import java.util.List;

/**
 * What a signal was worth on a stretch of history, before any question of how it would be traded.
 *
 * @param bars                  candles the signal was evaluated on
 * @param firedBars             those where it came out non-zero; a signal that rarely fires can be
 *                              excellent and still useless
 * @param lag1Autocorrelation   of bar-to-bar returns, which is a fact about the data rather than
 *                              the signal. Strongly negative means the price series bounces between
 *                              bid and ask, and any trend read off closes is reading that bounce
 * @param horizons              one entry per holding period measured
 */
public record PredictivenessReport(
    long bars,
    long firedBars,
    double lag1Autocorrelation,
    List<HorizonStat> horizons
) {
    /**
     * @param horizon               bars held
     * @param samples               signal-and-outcome pairs behind the numbers
     * @param executableCorrelation between the signal and the return of a position opened at the
     *                              open of the <em>next</em> bar - the earliest one a strategy
     *                              reacting to this bar could trade
     * @param sameBarCorrelation    the same against a position opened at the open of the bar the
     *                              signal was read from. Not tradable: the signal used that bar's
     *                              close. Reported because the gap between the two is the size of
     *                              the look-ahead a zero-latency backtest would have paid out
     * @param edgeBasisPoints       mean return in the signal's own direction, over the executable
     *                              entry, for signals past the threshold. This is the number that
     *                              has to beat commission and spread for the signal to be worth
     *                              trading at all
     * @param edgeSamples           how many signals passed the threshold
     * @param edgeStandardErrorBasisPoints how far the edge could be from the truth by luck alone.
     *                              Read it before the edge: a large edge over a handful of trades
     *                              is a number the next handful will not repeat
     * @param baselineBasisPoints   mean return over the same horizon across every bar, signal or
     *                              not - what simply holding the instrument paid. An edge below it
     *                              is not a signal, it is the drift of a market that went one way,
     *                              picked up by anything biased in that direction
     */
    public record HorizonStat(
        int horizon,
        long samples,
        double executableCorrelation,
        double sameBarCorrelation,
        double edgeBasisPoints,
        long edgeSamples,
        double edgeStandardErrorBasisPoints,
        double baselineBasisPoints
    ) {
        /**
         * The correlation two standard errors away from zero at this sample size. A correlation
         * inside it says nothing, however suggestive it looks.
         * <p>
         * It means what it says only because the samples behind it no longer overlap - see
         * {@link com.siberalt.singularity.strategy.analysis.SignalPredictiveness#strideFor(int)}. A
         * horizon measured every bar would count each stretch of future sixty times over and put the
         * floor eight times lower than it belongs.
         */
        public double noiseFloor() {
            return samples > 3 ? 2 / Math.sqrt(samples - 3) : 1;
        }

        public boolean isAboveNoise() {
            return Math.abs(executableCorrelation) > noiseFloor();
        }

        /**
         * Below this many trades an edge is not judged at all. A standard error worked out from a
         * handful of them is itself a guess - the first run to print one starred four hundred basis
         * points a trade that had come from two trades, which happened to agree.
         */
        private static final long TRADES_BEFORE_AN_ERROR_MEANS_ANYTHING = 30;

        /**
         * Whether the edge stands clear of its own scatter - twice its standard error, the same
         * two-sigma rule {@link #noiseFloor()} applies to the correlation.
         * <p>
         * A correlation past the floor and an edge inside its own error are not a contradiction:
         * the correlation is measured over every sampled bar, the edge only over the few whose
         * signal was strong enough to trade. A signal can know something about the market in
         * general and still have said nothing worth acting on often enough to prove it.
         */
        public boolean isEdgeAboveNoise() {
            return edgeSamples >= TRADES_BEFORE_AN_ERROR_MEANS_ANYTHING
                && Math.abs(edgeBasisPoints) > 2 * edgeStandardErrorBasisPoints;
        }

        /** What the signal added over holding, per trade. The only figure a cost can be set against. */
        public double excessBasisPoints() {
            return edgeBasisPoints - baselineBasisPoints;
        }
    }
}
