package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetPositionQuantityTest {
    private static final Instant NOW = Instant.parse("2021-06-03T10:00:00Z");

    private final TargetPositionQuantity quantity = new TargetPositionQuantity();

    @Test
    void commitsTheWholeAccountOnFullConviction() {
        assertEquals(1000, quantity.toBuy(moment(1.0), TradeCapacity.of(1000, 0)));
    }

    @Test
    void buysTheDifferenceUpToTheTarget() {
        // Eight tenths of a thousand-lot account is 800, and 300 of them are already held.
        assertEquals(500, quantity.toBuy(moment(0.8), TradeCapacity.of(700, 300)));
    }

    /**
     * The whole point of sizing to a target. The same signal on the next bar asks for nothing more,
     * where an incremental sizing would spend the leftover balance again and keep buying for as
     * long as the signal held.
     */
    @Test
    void asksForNothingOnceTheTargetIsMet() {
        assertEquals(0, quantity.toBuy(moment(0.8), TradeCapacity.of(200, 800)));
    }

    @Test
    void addsOnlyWhenTheSignalStrengthens() {
        assertEquals(0, quantity.toBuy(moment(0.8), TradeCapacity.of(200, 800)));
        assertEquals(100, quantity.toBuy(moment(0.9), TradeCapacity.of(200, 800)));
    }

    /**
     * A signal to sell wants nothing held, so the target is nothing and the position goes. Selling
     * a share of it would leave the strategy invested against its own signal.
     */
    @Test
    void closesThePositionOnAnySellSignal() {
        assertEquals(1000, quantity.toSell(moment(-1.0), TradeCapacity.of(0, 1000)));
        assertEquals(1000, quantity.toSell(moment(-0.5), TradeCapacity.of(0, 1000)));
    }

    @Test
    void sellsDownToTheTargetWhenTheSignalOnlyWeakens() {
        // Still a buy signal, just a weaker one: three tenths of the account, not eight.
        assertEquals(500, quantity.toSell(moment(0.3), TradeCapacity.of(200, 800)));
    }

    @Test
    void neverReturnsANegativeSize() {
        assertEquals(0, quantity.toBuy(moment(0.3), TradeCapacity.of(200, 800)));
        assertEquals(0, quantity.toSell(moment(1.0), TradeCapacity.of(200, 800)));
    }

    /**
     * The target falls as the price rises: the money left over buys fewer lots, so the account
     * measured in lots is smaller, and a signal that has not changed asks for nothing more.
     */
    @Test
    void doesNotChaseARisingPrice() {
        // 800 lots held, 200 more affordable - fully sized at 0.8 of a thousand.
        assertEquals(0, quantity.toBuy(moment(0.8), TradeCapacity.of(200, 800)));

        // The price doubled: the same money now buys 100. The account is 900 lots, target 720.
        assertEquals(0, quantity.toBuy(moment(0.8), TradeCapacity.of(100, 800)));
    }

    @Test
    void roundsDownToWholeLots() {
        // Seven tenths of three lots is 2.1, and there is no such thing as a tenth of a lot.
        assertEquals(2, quantity.toBuy(moment(0.7), TradeCapacity.of(3, 0)));
    }

    @Test
    void holdsOnlyTheShareOfTheAccountItIsAllowed() {
        // A tenth of a thousand-lot account at full conviction, not the whole of it.
        assertEquals(100, quantity.setFullPositionShare(0.1).toBuy(moment(1.0), TradeCapacity.of(1000, 0)));
    }

    @Test
    void scalesTheAllowedShareByConviction() {
        assertEquals(80, quantity.setFullPositionShare(0.1).toBuy(moment(0.8), TradeCapacity.of(1000, 0)));
    }

    @Test
    void sellsBackDownToTheAllowedShare() {
        // Nine hundred held where a tenth of the account is all the strategy means to hold.
        assertEquals(800, quantity.setFullPositionShare(0.1).toSell(moment(1.0), TradeCapacity.of(100, 900)));
    }

    @Test
    void refusesAShareOutsideItsRange() {
        assertThrows(IllegalArgumentException.class, () -> quantity.setFullPositionShare(0));
        assertThrows(IllegalArgumentException.class, () -> quantity.setFullPositionShare(1.5));
    }

    private TradeMoment moment(double signal) {
        return new TradeMoment("TMOS", NOW, new Upside(signal, 1.0));
    }
}
