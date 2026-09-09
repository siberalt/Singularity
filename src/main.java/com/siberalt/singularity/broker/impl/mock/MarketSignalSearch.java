package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandlePriceField;
import com.siberalt.singularity.entity.candle.ComparisonOperator;
import com.siberalt.singularity.entity.candle.FindPriceParams;
import com.siberalt.singularity.entity.order.Order;

import java.time.Instant;

/**
 * Finds the moment a waiting order can next trade, by looking ahead through the candle history
 * rather than asking again on every tick.
 * <p>
 * Two questions in one, and both have to be answered yes. Does the market reach the order's price -
 * for a limit order that means a bar whose low comes down to a buy's limit, or whose high comes up
 * to a sell's; an order with no price named waits only for a market to exist at all. And did that
 * bar trade enough to be worth waiting for: a bar too thin to give anyone a single lot is skipped
 * rather than treated as a fill of nothing.
 * <p>
 * How much of the bar the order will actually get is not answered here and cannot be. The bar is
 * shared with every other order working against the same instrument, and what is left of it is
 * known only once the moment arrives.
 */
public class MarketSignalSearch {
    private final SimulationMarketData marketDataService;
    private final OrderPriceModel priceModel;
    private final LiquidityModel liquidityModel;

    public MarketSignalSearch(
        SimulationMarketData marketDataService,
        OrderPriceModel priceModel,
        LiquidityModel liquidityModel
    ) {
        this.marketDataService = marketDataService;
        this.priceModel = priceModel;
        this.liquidityModel = liquidityModel;
    }

    /**
     * The first bar in {@code [from, to]} this order could trade against, or {@code null} if there
     * is none.
     */
    public Candle nextTradableBar(Order order, Instant from, Instant to) {
        Instant searchFrom = from;

        while (!searchFrom.isAfter(to)) {
            Candle candle = priceModel.isLimit(order)
                ? barReachingLimit(order, searchFrom, to)
                : marketDataService.nextCandleAtOrAfter(order.getInstrument().getUid(), searchFrom).orElse(null);

            // Outside the window there is nothing left to trade against. The lower bound matters as
            // much as the upper one: a bar before the search start is one this order has already
            // been given everything from, and taking it again would set the order to fill on a
            // moment it is standing on - forever.
            if (candle == null || candle.getTime().isBefore(searchFrom) || candle.getTime().isAfter(to)) {
                return null;
            }

            if (liquidityModel.barCapacity(candle, order.getDirection()) > 0) {
                return candle;
            }

            searchFrom = candle.getTime().plusMillis(1);
        }

        return null;
    }

    /**
     * The first bar in which the market reaches the order's limit price.
     * <p>
     * A parked buy is waiting for the price to come down to it, so it triggers on the first bar
     * whose low touches the limit; a parked sell waits for the price to come up, and triggers on a
     * high. Comparing against the bar's open instead would ask a different question - where the
     * price stood at one instant - and would miss every limit the market crossed inside a bar.
     */
    protected Candle barReachingLimit(Order order, Instant from, Instant to) {
        boolean isBuy = order.getDirection() == OrderDirection.BUY;

        return marketDataService.findByPrice(
                CandleInterval.MIN_1,
                new FindPriceParams(
                    order.getInstrument().getUid(),
                    from,
                    to,
                    order.getRequestedPrice(),
                    isBuy ? CandlePriceField.LOW : CandlePriceField.HIGH,
                    isBuy ? ComparisonOperator.LESS_OR_EQUAL : ComparisonOperator.MORE_OR_EQUAL,
                    1
                )
            )
            .stream()
            .findFirst()
            .orElse(null);
    }
}
