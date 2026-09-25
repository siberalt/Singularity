package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Passes the delegate's signal on and, a fixed number of bars later, says the opposite of it.
 * <p>
 * A position opened on the signal is therefore closed by the clock rather than by an opinion: the
 * delegate is not consulted at all while the count runs, and what comes out at the end is the reverse of
 * what went in, at full confidence. Signals arriving during the wait are ignored, which is what makes the
 * holding time fixed.
 * <p>
 * Which signals are worth starting the clock for is the caller's to say, and it has to be said by value.
 * Asking whether the delegate returned {@link Upside#NEUTRAL} is a comparison of identity, and a delegate
 * that builds a fresh {@code Upside} for every bar - as most do - never returns that constant, so a
 * threshold of a decorator in front of it would be bypassed entirely and the clock would start on any bar
 * at all. Hence {@code minSignal}: the delegate's signal has to reach it, in absolute value, before
 * anything happens.
 * <p>
 * The side matters as much as the size, and the two sides are different trades rather than mirrors of each
 * other. On a long-only account:
 * <ul>
 *   <li>{@link #ofRises} buys the signal and sells it out {@code waitBars} later - a long of fixed
 *   length, taken in the direction the move was already going;</li>
 *   <li>{@link #ofFalls} sells on the signal, which closes whatever was held, and buys back
 *   {@code waitBars} later - so the account steps aside for a fall and returns after it, and the position
 *   it takes is held until the next fall rather than for a fixed time;</li>
 *   <li>either side at once is the two woven together, and then the buy that follows a fall has no exit
 *   of its own: it waits for whichever sell arrives next, which may be months away. That is a holding
 *   time nobody chose, so it is worth choosing a side unless both are really wanted.</li>
 * </ul>
 */
public class FixedSignalReverserUpsideCalculator implements UpsideCalculator {
    private final UpsideCalculator delegate;

    private final int waitBars;

    private final double minSignal;

    /** Which way a signal has to point to be acted on: above zero for rises, below for falls, zero for both. */
    private final int side;

    private int barsSinceSignal = -1;

    private double direction;

    /**
     * @param delegate  where the signals come from
     * @param waitBars  how many bars after a signal its reverse is given
     * @param minSignal how strong a signal has to be, in absolute value, to start the count
     * @param side      which way it has to point: above zero for rises, below for falls, zero for either
     */
    protected FixedSignalReverserUpsideCalculator(UpsideCalculator delegate, int waitBars, double minSignal,
                                                  int side) {
        if (minSignal <= 0) {
            throw new IllegalArgumentException("The smallest signal worth acting on must be above zero");
        }

        this.delegate = delegate;
        this.waitBars = waitBars;
        this.minSignal = minSignal;
        this.side = side;
    }

    /** Acts on a signal pointing either way. */
    public FixedSignalReverserUpsideCalculator(UpsideCalculator delegate, int waitBars, double minSignal) {
        this(delegate, waitBars, minSignal, 0);
    }

    /** Acts on any signal that has a direction at all, pointing either way. */
    public FixedSignalReverserUpsideCalculator(UpsideCalculator delegate, int waitBars) {
        this(delegate, waitBars, Double.MIN_VALUE, 0);
    }

    /** Acts on rises alone: buys the signal, sells it out {@code waitBars} later. */
    public static FixedSignalReverserUpsideCalculator ofRises(UpsideCalculator delegate, int waitBars,
                                                             double minSignal) {
        return new FixedSignalReverserUpsideCalculator(delegate, waitBars, minSignal, 1);
    }

    /** Acts on falls alone: sells on the signal, buys back {@code waitBars} later. */
    public static FixedSignalReverserUpsideCalculator ofFalls(UpsideCalculator delegate, int waitBars,
                                                             double minSignal) {
        return new FixedSignalReverserUpsideCalculator(delegate, waitBars, minSignal, -1);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (barsSinceSignal >= 0) {
            if (barsSinceSignal == waitBars) {
                barsSinceSignal = -1;

                return new Upside(direction, 1);
            }

            barsSinceSignal++;

            return Upside.NEUTRAL;
        }

        Upside upside = delegate.calculate(lastCandles);

        if (Math.abs(upside.signal()) < minSignal || side * upside.signal() < 0) {
            return Upside.NEUTRAL;
        }

        barsSinceSignal = 0;
        direction = -Math.signum(upside.signal());

        return upside;
    }
}
