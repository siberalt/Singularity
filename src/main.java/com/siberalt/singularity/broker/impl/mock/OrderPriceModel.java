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
    /**
     * Where inside the bar a best-price order is assumed to trade: three tenths up from the low for
     * a buy, seven tenths for a sell - better than the middle, well short of the extreme.
     * <p>
     * This is a guess, not a measurement. Nothing in a candle says whether an order routed for the
     * best price would have caught the low of the bar or missed it, and a backtest leaning on
     * best-price orders is leaning on these two numbers - calibrate them against real fills before
     * trusting such a result.
     * <p>
     * Note which way the default leans: buying below the middle of the bar and selling above it is
     * an <em>optimistic</em> assumption, and it flatters every best-price order in the run. Two
     * alternatives worth knowing, neither of which needs calibrating:
     * <ul>
     *   <li>0.5 for both - neutral, the middle of the bar's range, assuming no skill and no bad
     *       luck in where inside the bar the order landed;</li>
     *   <li>1.0 for buys and 0.0 for sells - the worst price the bar ever showed, which gives a
     *       lower bound on what the strategy could have earned.</li>
     * </ul>
     * A result that survives the pessimistic pair is worth rather more than one that needs the
     * optimistic one.
     */
    public static final double DEFAULT_BUY_BEST_PRICE_RATIO = 0.3;
    public static final double DEFAULT_SELL_BEST_PRICE_RATIO = 0.7;

    private double buyBestPriceRatio = DEFAULT_BUY_BEST_PRICE_RATIO;
    private double sellBestPriceRatio = DEFAULT_SELL_BEST_PRICE_RATIO;
    private double halfSpreadRatio = 0;
    private double slippageImpactRatio = 0;

    public double getBuyBestPriceRatio() {
        return buyBestPriceRatio;
    }

    public OrderPriceModel setBuyBestPriceRatio(double buyBestPriceRatio) {
        this.buyBestPriceRatio = buyBestPriceRatio;
        return this;
    }

    public double getSellBestPriceRatio() {
        return sellBestPriceRatio;
    }

    public OrderPriceModel setSellBestPriceRatio(double sellBestPriceRatio) {
        this.sellBestPriceRatio = sellBestPriceRatio;
        return this;
    }

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
     */
    public Quotation currentPrice(OrderType orderType, OrderDirection orderDirection, Candle currentCandle) {
        double bestPriceRatio = switch (orderDirection) {
            case BUY -> buyBestPriceRatio;
            case SELL -> sellBestPriceRatio;
            case UNSPECIFIED -> 1;
        };

        orderType = Objects.requireNonNullElse(orderType, OrderType.LIMIT);

        return switch (orderType) {
            case UNSPECIFIED, LIMIT, MARKET -> currentCandle.open();
            case BEST_PRICE -> bestPrice(currentCandle, bestPriceRatio);
        };
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
            currentPrice(order.getOrderType(), order.getDirection(), candle),
            order.getDirection(),
            halfSpreadRatio + slippage(lots, candle)
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
    protected double slippage(long lots, Candle candle) {
        if (slippageImpactRatio == 0 || candle.volume() <= 0) {
            return 0;
        }

        return slippageImpactRatio * Math.min(1d, (double) lots / candle.volume());
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

    protected Quotation bestPrice(Candle candle, double bestPriceRatio) {
        Quotation priceRange = candle.high().subtract(candle.low());

        return candle
            .low()
            .add(priceRange.multiply(BigDecimal.valueOf(bestPriceRatio)));
    }

    private double requireRatio(double ratio, String name) {
        if (ratio < 0 || ratio >= 1) {
            throw new IllegalArgumentException(String.format("%s must be within [0, 1), got %s", name, ratio));
        }

        return ratio;
    }
}
