package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderPriceModelTest {
    private final OrderPriceModel priceModel = new OrderPriceModel();

    @Test
    void chargesNothingBeyondTheMarketPriceByDefault() {
        assertEquals(0, priceModel.getHalfSpreadRatio());
        assertEquals(0, priceModel.getSlippageImpactRatio());
        assertEquals(
            Quotation.of(100),
            priceModel.fillPrice(order(OrderType.MARKET, OrderDirection.BUY, null), bar(100), 10)
        );
    }

    /**
     * The two sides of the spread are what the same bar costs a buyer and pays a seller. Half a
     * percent either way of a hundred is 100.5 against 99.5.
     */
    @Test
    void makesABuyPayTheSpreadAndASellGiveItUp() {
        priceModel.setHalfSpreadRatio(0.005);

        assertEquals(
            Quotation.of(100.5),
            priceModel.fillPrice(order(OrderType.MARKET, OrderDirection.BUY, null), bar(100), 10)
        );
        assertEquals(
            Quotation.of(99.5),
            priceModel.fillPrice(order(OrderType.MARKET, OrderDirection.SELL, null), bar(100), 10)
        );
    }

    /**
     * A limit order is the side others cross to, so it pays neither the spread nor impact - it gets
     * the price it named and nothing worse.
     */
    @Test
    void chargesALimitOrderNeitherSpreadNorImpact() {
        priceModel.setHalfSpreadRatio(0.005).setSlippageImpactRatio(0.02);

        assertEquals(
            Quotation.of(95),
            priceModel.fillPrice(order(OrderType.LIMIT, OrderDirection.BUY, Quotation.of(95)), bar(100), 10)
        );
    }

    /**
     * A best-price order carries a price too, but it is not the one it trades at - it is not a
     * limit, so it crosses the spread like any other order taking liquidity.
     */
    @Test
    void treatsABestPriceOrderAsTakingLiquidityEvenWhenItCarriesAPrice() {
        priceModel.setHalfSpreadRatio(0.005);

        Candle candle = new Candle(
            "TEST",
            new TimePoint(Instant.parse("2021-12-15T15:00:00Z")),
            Quotation.of(100),
            Quotation.of(100),
            Quotation.of(110),
            Quotation.of(100),
            1000
        );

        // Three tenths up a range of ten is 103, and the spread is charged on top of it.
        assertEquals(
            Quotation.of(103.515),
            priceModel.fillPrice(order(OrderType.BEST_PRICE, OrderDirection.BUY, Quotation.of(95)), candle, 10)
        );
    }

    @Test
    void movesThePriceInProportionToTheShareOfTheBarTaken() {
        priceModel.setSlippageImpactRatio(0.02);

        // Half of a bar of 100 lots moves the price half as far as a whole bar would: one percent.
        assertEquals(
            Quotation.of(101),
            priceModel.fillPrice(order(OrderType.MARKET, OrderDirection.BUY, null), bar(100), 50)
        );
        // A tenth of the bar, a tenth of the move.
        assertEquals(
            Quotation.of(100.2),
            priceModel.fillPrice(order(OrderType.MARKET, OrderDirection.BUY, null), bar(100), 10)
        );
    }

    @Test
    void addsImpactOnTopOfTheSpread() {
        priceModel.setHalfSpreadRatio(0.005).setSlippageImpactRatio(0.02);

        assertEquals(
            Quotation.of(101.5),
            priceModel.fillPrice(order(OrderType.MARKET, OrderDirection.BUY, null), bar(100), 50)
        );
    }

    @Test
    void chargesNoImpactAgainstABarThatRecordsNoVolume() {
        priceModel.setSlippageImpactRatio(0.02);

        // Nothing to measure the order against, and a guess would be worse than none.
        assertEquals(
            Quotation.of(100),
            priceModel.fillPrice(order(OrderType.MARKET, OrderDirection.BUY, null), bar(0), 10)
        );
    }

    @Test
    void neverMovesThePriceFurtherThanAWholeBarWould() {
        priceModel.setSlippageImpactRatio(0.02);

        // An order larger than everything the bar traded still only pays the full impact once.
        assertEquals(
            Quotation.of(102),
            priceModel.fillPrice(order(OrderType.MARKET, OrderDirection.BUY, null), bar(100), 100000)
        );
    }

    @Test
    void refusesRatiosThatAreNotShares() {
        assertThrows(IllegalArgumentException.class, () -> priceModel.setHalfSpreadRatio(-0.1));
        assertThrows(IllegalArgumentException.class, () -> priceModel.setHalfSpreadRatio(1));
        assertThrows(IllegalArgumentException.class, () -> priceModel.setSlippageImpactRatio(-0.1));
        assertThrows(IllegalArgumentException.class, () -> priceModel.setSlippageImpactRatio(1));
    }

    private Order order(OrderType orderType, OrderDirection direction, Quotation requestedPrice) {
        return new Order()
            .setOrderType(orderType)
            .setDirection(direction)
            .setRequestedPrice(requestedPrice)
            .setLotsRequested(10)
            .setInstrument(new Instrument().setUid("TEST").setCurrency("RUB").setLot(1));
    }

    private Candle bar(long volume) {
        return new Candle(
            "TEST",
            new TimePoint(Instant.parse("2021-12-15T15:00:00Z")),
            Quotation.of(100),
            Quotation.of(100),
            Quotation.of(100),
            Quotation.of(100),
            volume
        );
    }
}
