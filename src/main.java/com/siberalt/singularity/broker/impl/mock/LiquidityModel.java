package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.entity.candle.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How much of an order one candle can actually absorb.
 * <p>
 * By default the answer is "all of it": {@link #isInfiniteLiquidity()} is on, every order fills in
 * full at a single price, and the simulation behaves as it always has. That is the wrong answer for
 * any order large relative to what the instrument actually traded - a backtest can then buy a
 * thousand lots into a bar that saw ten, and report a profit no real account could have taken.
 * Turning the flag off caps each fill at {@link #getParticipationRate()} of the bar's volume, and
 * whatever is left over keeps working.
 * <p>
 * The cap is deliberately crude. A candle records what traded, not what was on offer, and one order
 * cannot expect to be the whole tape - the participation rate is the share of the bar this account
 * assumes it could have taken. It is an assumption to be set per simulation, not a measurement.
 */
public class LiquidityModel {
    /**
     * A tenth of the bar. Conventional for participation-rate models and conservative enough that
     * an order only starts splitting when it is genuinely large for the instrument.
     */
    public static final double DEFAULT_PARTICIPATION_RATE = 0.1;

    private boolean infiniteLiquidity = true;
    private double participationRate = DEFAULT_PARTICIPATION_RATE;

    public boolean isInfiniteLiquidity() {
        return infiniteLiquidity;
    }

    public LiquidityModel setInfiniteLiquidity(boolean infiniteLiquidity) {
        this.infiniteLiquidity = infiniteLiquidity;
        return this;
    }

    public double getParticipationRate() {
        return participationRate;
    }

    public LiquidityModel setParticipationRate(double participationRate) {
        if (participationRate <= 0 || participationRate > 1) {
            throw new IllegalArgumentException(
                String.format("Participation rate must be within (0, 1], got %s", participationRate)
            );
        }

        this.participationRate = participationRate;
        return this;
    }

    /**
     * How many of {@code lotsWanted} this candle can take, between 0 and {@code lotsWanted}. Zero
     * means the bar traded too little to give the order even one lot, so nothing fills here at all.
     *
     * @param candle the bar the fill would happen on, whose volume is read in lots - the same unit
     *               an order counts in
     */
    public long fillableLots(long lotsWanted, Candle candle) {
        if (infiniteLiquidity) {
            return lotsWanted;
        }

        long available = BigDecimal.valueOf(candle.volume())
            .multiply(BigDecimal.valueOf(participationRate))
            .setScale(0, RoundingMode.DOWN)
            .longValue();

        return Math.min(lotsWanted, Math.max(available, 0));
    }
}
