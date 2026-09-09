package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidityModelTest {
    private static final OrderDirection BUY = OrderDirection.BUY;
    private static final OrderDirection SELL = OrderDirection.SELL;
    private static final String INSTRUMENT = "TEST";
    private static final Instant BAR_TIME = Instant.parse("2021-12-15T15:00:00Z");

    private final LiquidityModel liquidityModel = new LiquidityModel();

    @Test
    void givesAnOrderEverythingItAsksForByDefault() {
        assertTrue(liquidityModel.isInfiniteLiquidity());
        // A bar that traded a single lot still fills an order a thousand times its size - which is
        // the point of the default: the simulation behaves as it did before there was a model here.
        assertEquals(1000, liquidityModel.take(INSTRUMENT, bar(1), BUY, 1000));
        // And it stays that way however many orders come through the same bar.
        assertEquals(1000, liquidityModel.take(INSTRUMENT, bar(1), BUY, 1000));
    }

    @Test
    void capsAFillAtItsShareOfWhatTheBarTraded() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(10, liquidityModel.take(INSTRUMENT, bar(100), BUY, 1000));
    }

    @Test
    void givesAnOrderNoMoreThanItAskedFor() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(3, liquidityModel.take(INSTRUMENT, bar(100), BUY, 3));
    }

    @Test
    void roundsDownSoAFillIsNeverLargerThanTheShareAllows() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        // A tenth of 99 is 9.9, and there is no such thing as nine tenths of a lot.
        assertEquals(9, liquidityModel.take(INSTRUMENT, bar(99), BUY, 1000));
    }

    @Test
    void refusesTheWholeFillWhenTheBarIsTooThinForASingleLot() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(0, liquidityModel.take(INSTRUMENT, bar(9), BUY, 1000));
        assertEquals(0, liquidityModel.take(INSTRUMENT, barAt(BAR_TIME.plusSeconds(60), 0), BUY, 1000));
    }

    /**
     * The point of the whole class: a bar is one stretch of tape. Handing every order its own share
     * of it independently would let the same volume be traded over and over, which is exactly the
     * fiction the model exists to remove.
     */
    @Test
    void sharesOneBarBetweenEveryOrderTradingAgainstIt() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        Candle bar = bar(100);

        assertEquals(6, liquidityModel.take(INSTRUMENT, bar, BUY, 6));
        // Four of the ten lots are left, so an order wanting six gets what remains and no more.
        assertEquals(4, liquidityModel.take(INSTRUMENT, bar, BUY, 6));
        // And the next one finds the bar used up.
        assertEquals(0, liquidityModel.take(INSTRUMENT, bar, BUY, 6));
    }

    @Test
    void keepsOneBudgetPerInstrument() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(10, liquidityModel.take(INSTRUMENT, bar(100), BUY, 1000));
        // A different instrument's bar is its own tape.
        assertEquals(10, liquidityModel.take("OTHER", bar(100), BUY, 1000));
        assertEquals(0, liquidityModel.take(INSTRUMENT, bar(100), BUY, 1000));
    }

    @Test
    void startsAFreshBudgetOnTheNextBar() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(10, liquidityModel.take(INSTRUMENT, bar(100), BUY, 1000));
        assertEquals(0, liquidityModel.take(INSTRUMENT, bar(100), BUY, 1000));

        Candle nextBar = barAt(BAR_TIME.plus(Duration.ofMinutes(1)), 100);

        assertEquals(10, liquidityModel.take(INSTRUMENT, nextBar, BUY, 1000));
    }

    /**
     * The other half of taking. Lots claimed by an order that turns out not to trade them would
     * otherwise sit held for the rest of the bar, denied to everyone behind it for nothing.
     */
    @Test
    void handsBackWhatWasClaimedButNotTraded() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        Candle bar = bar(100);

        assertEquals(10, liquidityModel.take(INSTRUMENT, bar, BUY, 10));
        assertEquals(0, liquidityModel.take(INSTRUMENT, bar, BUY, 1));

        liquidityModel.give(INSTRUMENT, bar, BUY, 4);

        assertEquals(4, liquidityModel.take(INSTRUMENT, bar, BUY, 10));
    }

    @Test
    void neverGivesBackMoreThanTheBarEverHad() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        Candle bar = bar(100);

        liquidityModel.take(INSTRUMENT, bar, BUY, 3);
        // More than was ever taken - the bar still cannot end up richer than it traded.
        liquidityModel.give(INSTRUMENT, bar, BUY, 1000);

        assertEquals(10, liquidityModel.take(INSTRUMENT, bar, BUY, 1000));
    }

    @Test
    void refusesAParticipationRateThatIsNotAShare() {
        assertThrows(IllegalArgumentException.class, () -> liquidityModel.setParticipationRate(0));
        assertThrows(IllegalArgumentException.class, () -> liquidityModel.setParticipationRate(-0.1));
        assertThrows(IllegalArgumentException.class, () -> liquidityModel.setParticipationRate(1.5));
    }

    @Test
    void takesTheWholeBarAtARateOfOne() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(1);

        assertEquals(100, liquidityModel.take(INSTRUMENT, bar(100), BUY, 1000));
    }

    /**
     * Where the feed says which side each trade came from, an order reaches only its own side. A
     * bar whose whole volume was sell-initiated has nothing in it for a buyer, and used to look
     * like a bar with room in it.
     */
    @Test
    void capsEachDirectionAgainstItsOwnSideOfTheFlow() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        Candle bar = split(800, 200);

        assertEquals(80, liquidityModel.take(INSTRUMENT, bar, BUY, 1000));
        assertEquals(20, liquidityModel.take(INSTRUMENT, bar, SELL, 1000));
    }

    @Test
    void leavesABuyerNothingInABarThatOnlySold() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(0, liquidityModel.take(INSTRUMENT, split(0, 1000), BUY, 1000));
        assertEquals(100, liquidityModel.take(INSTRUMENT, split(0, 1000), SELL, 1000));
    }

    /** Buys and sells are not competing for the same lots, so one cannot use the other up. */
    @Test
    void keepsSeparateBudgetsForBuyingAndSelling() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        Candle bar = split(500, 500);

        assertEquals(50, liquidityModel.take(INSTRUMENT, bar, BUY, 1000));
        assertEquals(0, liquidityModel.take(INSTRUMENT, bar, BUY, 1000));
        assertEquals(50, liquidityModel.take(INSTRUMENT, bar, SELL, 1000));
    }

    /** The history recorded before the feed reported the split has only the whole bar to go on. */
    @Test
    void fallsBackToTheWholeBarWithoutASplit() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(10, liquidityModel.take(INSTRUMENT, bar(100), BUY, 1000));
        assertEquals(10, liquidityModel.take(INSTRUMENT, bar(100), SELL, 1000));
    }

    /** A bar carrying the split, for the direction-aware cases above. */
    private Candle split(long buy, long sell) {
        return new Candle(
            INSTRUMENT,
            new TimePoint(BAR_TIME),
            Quotation.of(10), Quotation.of(10), Quotation.of(10), Quotation.of(10),
            buy + sell, buy, sell
        );
    }

    private Candle bar(long volume) {
        return barAt(BAR_TIME, volume);
    }

    private Candle barAt(Instant time, long volume) {
        return new Candle(
            INSTRUMENT,
            new TimePoint(time),
            Quotation.of(10),
            Quotation.of(10),
            Quotation.of(10),
            Quotation.of(10),
            volume
        );
    }
}
