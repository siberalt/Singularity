package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquidityModelTest {
    private final LiquidityModel liquidityModel = new LiquidityModel();

    @Test
    void givesAnOrderEverythingItAsksForByDefault() {
        assertTrue(liquidityModel.isInfiniteLiquidity());
        // A bar that traded a single lot still fills an order a thousand times its size - which is
        // the point of the default: the simulation behaves as it did before there was a model here.
        assertEquals(1000, liquidityModel.fillableLots(1000, candleWithVolume(1)));
    }

    @Test
    void capsAFillAtItsShareOfWhatTheBarTraded() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(10, liquidityModel.fillableLots(1000, candleWithVolume(100)));
    }

    @Test
    void givesAnOrderNoMoreThanItAskedFor() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(3, liquidityModel.fillableLots(3, candleWithVolume(100)));
    }

    @Test
    void roundsDownSoAFillIsNeverLargerThanTheShareAllows() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        // A tenth of 99 is 9.9, and there is no such thing as nine tenths of a lot.
        assertEquals(9, liquidityModel.fillableLots(1000, candleWithVolume(99)));
    }

    @Test
    void refusesTheWholeFillWhenTheBarIsTooThinForASingleLot() {
        liquidityModel.setInfiniteLiquidity(false).setParticipationRate(0.1);

        assertEquals(0, liquidityModel.fillableLots(1000, candleWithVolume(9)));
        assertEquals(0, liquidityModel.fillableLots(1000, candleWithVolume(0)));
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

        assertEquals(100, liquidityModel.fillableLots(1000, candleWithVolume(100)));
    }

    private Candle candleWithVolume(long volume) {
        return new Candle(
            "TEST",
            new TimePoint(Instant.parse("2021-12-15T15:00:00Z")),
            Quotation.of(10),
            Quotation.of(10),
            Quotation.of(10),
            Quotation.of(10),
            volume
        );
    }
}
