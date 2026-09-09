package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.order.Order;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Decides the price an order fills at, given the candle covering the moment of the fill. Kept apart
 * from {@link MockOrderService} because both the service (pricing an order as it is posted) and
 * {@link SimulatedPendingOrderHandler} (pricing an order against the candle that finally triggers
 * it) need the same answer.
 * <p>
 * A candle records where the price went, not what it cost to trade there, so everything below the
 * reference price is an assumption. Both are off by default, and both are set per simulation:
 * <ul>
 *   <li>{@link #setHalfSpreadRatio} - what crossing the spread costs. An order that takes liquidity
 *       does not trade at the mid: it buys at the ask and sells at the bid.</li>
 *   <li>{@link #setSlippageImpactRatio} - what the order's own size costs, in proportion to the
 *       share of the bar it takes. Large orders move the price they are trying to get.</li>
 * </ul>
 * Neither applies to a limit order. A limit order is the passive side of the trade - it is the one
 * posting the price others cross to, so it earns the spread rather than paying it, and it never
 * fills worse than the price it named.
 */
public class OrderPriceModel {
    private double halfSpreadRatio = 0;
    private double slippageImpactRatio = 0;

    public double getHalfSpreadRatio() {
        return halfSpreadRatio;
    }

    /**
     * Half the bid-ask spread as a fraction of the price, so 0.0005 means a spread of ten basis
     * points: a buy pays five over the reference price and a sell gets five under it.
     * <p>
     * The floor is not a matter of taste - a spread cannot be tighter than one price tick, so
     * whatever else is true, {@code halfSpread >= tick / (2 * price)}. That ratio is worth working
     * out for the instrument being simulated, because a cheap instrument has a coarse tick relative
     * to its price: TMOS in 2021 traded around 6.51 roubles on a tick of 0.002, which is 3.1 basis
     * points, and a one-tick spread there already costs 0.00015. Wide-spread names run several ticks
     * on top of that.
     * <p>
     * Liquid large caps sit around 1 to 5 basis points of spread (0.00005 to 0.00025 here); thinner
     * names run to tens of points. If the instrument's own quotes are to hand, they beat any of
     * these numbers - measure rather than guess.
     */
    public OrderPriceModel setHalfSpreadRatio(double halfSpreadRatio) {
        this.halfSpreadRatio = requireRatio(halfSpreadRatio, "Half spread ratio");
        return this;
    }

    public double getSlippageImpactRatio() {
        return slippageImpactRatio;
    }

    /**
     * How far the price moves against an order that takes the whole bar. An order taking a tenth of
     * the bar moves it a tenth as far, so at 0.01 such an order pays ten basis points of impact on
     * top of the spread.
     * <p>
     * A usable way to pick it: sweeping an entire bar costs roughly that bar's own high-to-low
     * range, so start from the average range of the bars being simulated. TMOS 1-minute bars in
     * 2021 averaged 0.060 per cent, which puts the figure near 0.0006 - and reassuringly, that is
     * about twice the tick, which is the order of magnitude one would expect.
     * <p>
     * Two things to know before trusting the result. Impact here is <em>linear</em> in the share of
     * the bar taken, while measured impact grows closer to its square root; calibrated so the whole
     * bar costs the right amount, this therefore under-charges every order smaller than a whole bar,
     * and the smaller the order the further out it is. And impact is charged per fill, so a large
     * order split across many bars pays it many times over - which is realistic in direction, but
     * means the total cost of a big order depends on
     * {@link LiquidityModel#setParticipationRate} as much as on this. Set the two together, and
     * re-check the resulting cost per round trip against something real.
     */
    public OrderPriceModel setSlippageImpactRatio(double slippageImpactRatio) {
        this.slippageImpactRatio = requireRatio(slippageImpactRatio, "Slippage impact ratio");
        return this;
    }

    /**
     * Where the instrument is trading, before any cost of trading it. This is the figure a limit
     * price is compared against and an order is quoted at while it waits - not what a fill costs.
     * <p>
     * The same for every type of order, BEST_PRICE included. It used to trade three tenths up from
     * the low of the bar for a buy and seven tenths for a sell, which was a claim that the order
     * landed in the better part of the minute - and nothing in the strategy or in the candle decides
     * where inside a bar an order lands. That handed every round trip four tenths of the bar range
     * for free, and at fifteen thousand round trips a year it was the whole of what a strategy
     * appeared to earn.
     * <p>
     * A best-price order is a real order type - the broker routes it to the top of the book, and
     * some instruments accept nothing else - but what makes it different from a market order is how
     * far down the book it is willing to go, not what part of the bar it catches. At bar resolution
     * that difference is about size, which {@link LiquidityModel} already answers, and not about
     * price.
     */
    public Quotation currentPrice(Candle currentCandle) {
        return currentCandle.open();
    }

    /**
     * What this fill actually goes through at: the price the order named if it named one, and
     * otherwise the market price plus what it costs to take it.
     *
     * @param lots the size of this particular fill, which is what its market impact is measured by
     */
    public Quotation fillPrice(Order order, Candle candle, long lots) {
        if (isLimit(order)) {
            return limitFillPrice(order.getDirection(), order.getRequestedPrice(), candle);
        }

        return worsen(
            currentPrice(candle),
            order.getDirection(),
            halfSpreadRatio + slippage(lots, candle, order.getDirection())
        );
    }

    /**
     * Whether the order named the price it is prepared to trade at, which is what makes it the
     * passive side. Decided by the order's type and not by whether it carries a price: a best-price
     * order may well carry one, and it is not a limit.
     */
    public boolean isLimit(Order order) {
        return Objects.requireNonNullElse(order.getOrderType(), OrderType.LIMIT) == OrderType.LIMIT;
    }

    /**
     * The price a limit order fills at on the candle that finally triggered it.
     * <p>
     * The limit is the worst price the order accepts, so that is what it normally gets - the market
     * only had to touch it. The exception is a gap: if the candle already opened past the limit,
     * the order fills at that open, because there was never anything to buy at the limit in
     * between. Taking the open unconditionally would be wrong the other way - it hands a buy a
     * price above its own limit whenever the candle merely dipped to it.
     */
    public Quotation limitFillPrice(OrderDirection orderDirection, Quotation limitPrice, Candle triggerCandle) {
        Quotation open = triggerCandle.open();

        return switch (orderDirection) {
            case BUY -> open.isLessThan(limitPrice) ? open : limitPrice;
            case SELL -> open.isGreaterThan(limitPrice) ? open : limitPrice;
            case UNSPECIFIED -> limitPrice;
        };
    }

    /**
     * The share of the bar this fill takes, scaled by how far a whole bar would move the price. A
     * bar that records no volume gets no impact - there is nothing to measure the fill against, and
     * guessing would be worse than admitting it.
     */
    protected double slippage(long lots, Candle candle, OrderDirection direction) {
        long reachable = candle.reachableVolume(direction);

        if (slippageImpactRatio == 0 || reachable <= 0) {
            return 0;
        }

        return slippageImpactRatio * Math.min(1d, (double) lots / reachable);
    }

    /**
     * Moves the price against the order - up for a buy, down for a sell - by the given fraction.
     */
    protected Quotation worsen(Quotation price, OrderDirection orderDirection, double ratio) {
        if (ratio == 0) {
            return price;
        }

        BigDecimal factor = BigDecimal.ONE.add(
            BigDecimal.valueOf(orderDirection == OrderDirection.SELL ? -ratio : ratio)
        );

        return price.multiply(factor);
    }

    private double requireRatio(double ratio, String name) {
        if (ratio < 0 || ratio >= 1) {
            throw new IllegalArgumentException(String.format("%s must be within [0, 1), got %s", name, ratio));
        }

        return ratio;
    }
}
