package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.entity.candle.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * How much of an order a bar can actually absorb.
 * <p>
 * By default the answer is "all of it": {@link #isInfiniteLiquidity()} is on, every order fills in
 * full at a single price, and the simulation behaves as it always has. That is the wrong answer for
 * any order large relative to what the instrument actually traded - a backtest can then buy a
 * thousand lots into a bar that saw ten, and report a profit no real account could have taken.
 * Turning the flag off caps what a bar gives at {@link #getParticipationRate()} of its volume, and
 * whatever is left over keeps working.
 * <p>
 * That cap is on the <em>bar</em>, not on each order. A bar is one stretch of tape, and every order
 * trading against it - whether posted now or waiting since yesterday - draws from the same budget
 * until it is used up. Handing each order its own share independently would let the same volume be
 * traded many times over, which is the very thing this class exists to stop.
 * <p>
 * Buys and sells keep separate budgets, because they are not competing for the same thing. An order
 * to buy takes what the offers hold and a sell takes what the bids hold; where the feed reports
 * which side each trade was initiated from, each direction is capped against its own side of the
 * flow. A bar where everything traded was sell-initiated offers a buyer nothing, and used to look
 * like a bar with room in it.
 * <p>
 * The cap is deliberately crude. A candle records what traded, not what was on offer, and one
 * account cannot expect to be the whole tape - the participation rate is the share of the bar this
 * account assumes it could have taken. It is an assumption to be set per simulation, not a
 * measurement.
 * <p>
 * Keeping the budget makes this class stateful, unlike the price model beside it. It holds one
 * running total per instrument, replaced as soon as a different bar is asked about, and expects the
 * single-threaded simulation it lives in.
 */
public class LiquidityModel {
    /**
     * A tenth of the bar. Conventional for participation-rate models and conservative enough that
     * an order only starts splitting when it is genuinely large for the instrument.
     *
     * @see #setParticipationRate
     */
    public static final double DEFAULT_PARTICIPATION_RATE = 0.1;

    private boolean infiniteLiquidity = true;
    private double participationRate = DEFAULT_PARTICIPATION_RATE;
    private final Map<BudgetKey, BarBudget> budgets = new HashMap<>();

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

    /**
     * The share of a bar's volume this account assumes it could have taken. Sensible values sit
     * between 0.05 and 0.20, and 0.10 is the usual starting point; lower makes the backtest more
     * pessimistic, which is the safe direction to be wrong in.
     * <p>
     * The rate is a share of the flow on the order's own side where the feed reports it, and of the
     * whole bar where it does not - so on an instrument without the split the same number is the
     * more generous of the two, by roughly double. Past roughly a quarter of the tape an order stops
     * being a participant and starts being the market, at which point neither this cap nor any
     * impact estimate laid on top of it means much.
     * <p>
     * What the rate costs in simulated time is worth a look before settling on it: at 0.10 against
     * TMOS in 2021, whose 1-minute bars averaged some 18 700 lots, an order takes about 1 900 lots
     * a minute - so a position worth a million roubles needs well over an hour to build. That is a
     * fact about the instrument rather than about this setting, and it is exactly the fact the
     * whole class exists to stop a backtest from hiding.
     */
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
     * Everything this bar has to give, before any order has taken from it. Used to tell a bar worth
     * waiting for from one too thin to trade against at all. What is left of it by the time an order
     * gets there is not asked here - only {@link #take} can answer that, and taking is the asking.
     */
    public long barCapacity(Candle candle, OrderDirection direction) {
        if (infiniteLiquidity) {
            return Long.MAX_VALUE;
        }

        return Math.max(
            0,
            BigDecimal.valueOf(candle.reachableVolume(direction))
                .multiply(BigDecimal.valueOf(participationRate))
                .setScale(0, RoundingMode.DOWN)
                .longValue()
        );
    }

    /**
     * Claims up to {@code lotsWanted} of what the bar has left, and returns what it actually gave.
     * Zero means the bar is used up: earlier orders took all of it, and this one has to wait for
     * the next.
     * <p>
     * Whatever is taken here and does not end up trading has to come back through
     * {@link #give} - a bar held by an order that never used it is a bar the orders behind it
     * were denied for nothing.
     */
    public long take(String instrumentUid, Candle candle, OrderDirection direction, long lotsWanted) {
        if (infiniteLiquidity) {
            return lotsWanted;
        }

        BarBudget budget = budgetOf(instrumentUid, candle, direction);
        long granted = Math.min(lotsWanted, budget.remaining);
        budget.remaining -= granted;

        return granted;
    }

    /**
     * Returns lots claimed but not traded, so the orders behind get their turn at them. The other
     * half of {@link #take}, and the same bargain the account makes with its money: hold back what
     * a fill might need, hand back what it turned out not to.
     */
    public void give(String instrumentUid, Candle candle, OrderDirection direction, long lots) {
        if (infiniteLiquidity || lots <= 0) {
            return;
        }

        BarBudget budget = budgetOf(instrumentUid, candle, direction);
        budget.remaining = Math.min(budget.remaining + lots, barCapacity(candle, direction));
    }

    /**
     * The running total for this instrument's current bar, started afresh whenever the bar changes.
     * Only one bar per instrument is ever live at once - the simulation trades the moment it is at -
     * so there is nothing to accumulate or evict.
     */
    private BarBudget budgetOf(String instrumentUid, Candle candle, OrderDirection direction) {
        BudgetKey key = new BudgetKey(instrumentUid, direction);
        BarBudget budget = budgets.get(key);

        if (budget == null || !budget.barTime.equals(candle.getTime())) {
            budget = new BarBudget(candle.getTime(), barCapacity(candle, direction));
            budgets.put(key, budget);
        }

        return budget;
    }

    private record BudgetKey(String instrumentUid, OrderDirection direction) {
    }

    private static final class BarBudget {
        private final Instant barTime;
        private long remaining;

        private BarBudget(Instant barTime, long remaining) {
            this.barTime = barTime;
            this.remaining = remaining;
        }
    }
}
