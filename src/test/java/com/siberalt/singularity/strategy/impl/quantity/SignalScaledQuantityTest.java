package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignalScaledQuantityTest {
    private static final Instant NOW = Instant.parse("2021-06-03T10:00:00Z");

    private final SignalScaledQuantity quantity = new SignalScaledQuantity();

    @Test
    void commitsTheWholeBalanceOnFullConviction() {
        assertEquals(1000, quantity.toBuy(moment(1.0), 1000));
    }

    @Test
    void scalesWithTheStrengthOfTheSignal() {
        assertEquals(700, quantity.toBuy(moment(0.7), 1000));
    }

    @Test
    void closesTheWholePositionOnFullConviction() {
        // The sell signal is negative, and it is its strength that decides how much goes.
        assertEquals(1000, quantity.toSell(moment(-1.0), 1000));
        assertEquals(500, quantity.toSell(moment(-0.5), 1000));
    }

    @Test
    void roundsDownToWholeLots() {
        // Seven tenths of three lots is 2.1, and there is no such thing as a tenth of a lot.
        assertEquals(2, quantity.toBuy(moment(0.7), 3));
    }

    private TradeMoment moment(double signal) {
        return new TradeMoment("TMOS", NOW, new Upside(signal, 1.0));
    }
}
