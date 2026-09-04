package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Decides the price an order fills at, given the candle covering the moment of the fill. Kept
 * apart from {@link MockOrderService} because both the service (pricing an order as it is posted)
 * and {@link SimulatedPendingOrderHandler} (pricing a limit order against the candle that finally
 * triggers it) need the same answer.
 * <p>
 * The model is deliberately simple: market and limit orders fill at the candle's open, best-price
 * orders at a fixed point along the candle's range. It has no spread and no slippage - this class
 * is the place to add them.
 */
public class OrderPriceModel {
    public static final double DEFAULT_BUY_BEST_PRICE_RATIO = 0.3;
    public static final double DEFAULT_SELL_BEST_PRICE_RATIO = 0.7;

    private double buyBestPriceRatio = DEFAULT_BUY_BEST_PRICE_RATIO;
    private double sellBestPriceRatio = DEFAULT_SELL_BEST_PRICE_RATIO;

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

    protected Quotation bestPrice(Candle candle, double bestPriceRatio) {
        Quotation priceRange = candle.high().subtract(candle.low());

        return candle
            .low()
            .add(priceRange.multiply(BigDecimal.valueOf(bestPriceRatio)));
    }
}
