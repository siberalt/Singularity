package com.siberalt.singularity.strategy.market.position;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.shared.TimePointRange;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PositionRiskCoefficientTest {
    private static final String ACCOUNT = "account";
    private static final String INSTRUMENT = "TEST";
    private static final Instant OPENED = Instant.parse("2024-01-01T00:00:00Z");
    private static final double VOLATILITY = 10;

    private EntryPriceCalculator positions;
    private ExtremeLocator maximums;
    private ExtremeLocator minimums;
    private VolatilityCalculator volatility;

    @BeforeEach
    void setUp() {
        positions = mock(EntryPriceCalculator.class);
        maximums = mock(ExtremeLocator.class);
        minimums = mock(ExtremeLocator.class);
        volatility = mock(VolatilityCalculator.class);

        when(volatility.calculate(anyList())).thenReturn(VOLATILITY);
        when(maximums.locate(anyList())).thenReturn(List.of());
        when(minimums.locate(anyList())).thenReturn(List.of());
    }

    @Test
    void readsNothingWithNoPositionOpen() {
        when(positions.calculate(any(), any())).thenReturn(EntryPrice.EMPTY);

        assertEquals(0, coefficient().of(bars(100)));
    }

    @Test
    void readsNothingWithoutCandlesToReadFrom() {
        assertEquals(0, coefficient().of(null));
        assertEquals(0, coefficient().of(List.of()));
    }

    /** Eighty below an entry of a thousand, at a volatility of ten, is eight volatilities under water. */
    @Test
    void countsHowFarALongIsUnderWaterInVolatilities() {
        holding(10, 1000);

        assertEquals(8, coefficient().of(bars(920)));
    }

    /** The reading is about the position, so a short is hurt by the same distance the other way. */
    @Test
    void countsAShortTheOtherWayRound() {
        holding(-10, 1000);

        assertEquals(8, coefficient().of(bars(1080)));
    }

    @Test
    void readsNegativeWhileThePositionIsAhead() {
        holding(10, 1000);

        assertEquals(-3, coefficient().of(bars(1030)));
    }

    /**
     * What makes it a trailing measure: once the price has run in the position's favour, the reference
     * is that high water mark rather than the entry price, so giving the run back reads as risk.
     */
    @Test
    void measuresFromTheHighWaterMarkOnceThePriceHasRun() {
        holding(10, 1000);
        when(maximums.locate(anyList())).thenReturn(List.of(candle("2024-01-01T02:00:00Z", 1100)));

        assertEquals(10, coefficient().of(bars(1000)));
    }

    /** A high from before the position was opened says nothing about this position. */
    @Test
    void ignoresAnExtremeOlderThanThePosition() {
        holding(10, 1000);
        when(maximums.locate(anyList())).thenReturn(List.of(candle("2023-12-31T22:00:00Z", 1100)));

        assertEquals(0, coefficient().of(bars(1000)));
    }

    @Test
    void refusesToBeBuiltWithoutWhatItReads() {
        assertThrows(IllegalArgumentException.class,
            () -> new PositionRiskCoefficient(ACCOUNT, null, volatility, maximums, minimums, Candle::close));
        assertThrows(IllegalArgumentException.class,
            () -> new PositionRiskCoefficient(ACCOUNT, positions, null, maximums, minimums, Candle::close));
        assertThrows(IllegalArgumentException.class,
            () -> new PositionRiskCoefficient(ACCOUNT, positions, volatility, maximums, minimums, null));
    }

    private PositionRiskCoefficient coefficient() {
        return new PositionRiskCoefficient(ACCOUNT, positions, volatility, maximums, minimums, Candle::close);
    }

    private void holding(long quantity, double averagePrice) {
        when(positions.calculate(ACCOUNT, INSTRUMENT)).thenReturn(new EntryPrice(
            quantity,
            Quotation.of(averagePrice),
            new TimePointRange(new TimePoint(OPENED))
        ));
    }

    private Candle candle(String time, double price) {
        return new CandleFactory(INSTRUMENT).createCommon(time, price);
    }

    private List<Candle> bars(double lastPrice) {
        List<Candle> candles = new ArrayList<>();

        candles.add(candle("2024-01-01T01:00:00Z", 1000));
        candles.add(candle("2024-01-01T03:00:00Z", lastPrice));

        return candles;
    }
}
