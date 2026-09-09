package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BarVolumeCappedQuantityTest {
    private static final String INSTRUMENT = "TMOS";
    private static final Instant NOW = Instant.parse("2021-06-03T10:00:00Z");

    private ReadCandleRepository candleRepository;
    private BarVolumeCappedQuantity quantity;

    @BeforeEach
    void setUp() {
        candleRepository = mock(ReadCandleRepository.class);
        quantity = new BarVolumeCappedQuantity(new SignalScaledQuantity(), candleRepository);
    }

    /**
     * The point of the cap: an order this size finishes in the bar the signal fired on, rather than
     * trailing across the next dozen and buying at prices the signal never saw.
     */
    @Test
    void holdsAnOrderDownToWhatOneBarCanAbsorb() {
        stubBars(1000);

        // A tenth of a thousand-lot bar.
        assertEquals(100, quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
        assertEquals(100, quantity.toSell(moment(-1.0), TradeCapacity.of(0, 100_000)));
    }

    /**
     * A bar hands a buyer what its offers held, not what both sides traded between them, so that is
     * what a share of it has to be measured against.
     */
    @Test
    void capsAgainstTheSideTheOrderTradesAgainst() {
        stubSplitBars(800, 200);

        assertEquals(80, quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
        assertEquals(20, quantity.toSell(moment(-1.0), TradeCapacity.of(0, 100_000)));
    }

    @Test
    void leavesASizeThatAlreadyFitsAlone() {
        stubBars(1000);

        assertEquals(40, quantity.toBuy(moment(1.0), TradeCapacity.of(40, 0)));
    }

    @Test
    void neverTurnsNothingIntoSomething() {
        stubBars(1000);

        assertEquals(0, quantity.toBuy(moment(1.0), TradeCapacity.of(0, 0)));
        assertEquals(0, quantity.toSell(moment(-1.0), TradeCapacity.of(0, 0)));
    }

    /**
     * Averaged over recent bars, not taken from the last one: the order fills against the bars that
     * come after the decision, and one unusually busy bar is no reason to send an order the next
     * one cannot take.
     */
    @Test
    void averagesRecentBarsRatherThanTrustingTheLastOne() {
        List<Candle> candles = new ArrayList<>();
        // One busy bar among three quiet ones - the average is 1 000, not the 4 000 spike.
        candles.add(candleAt(NOW, 4000));
        candles.add(candleAt(NOW.minus(Duration.ofMinutes(1)), 0));
        candles.add(candleAt(NOW.minus(Duration.ofMinutes(2)), 0));
        candles.add(candleAt(NOW.minus(Duration.ofMinutes(3)), 0));
        when(candleRepository.findBeforeOrEqual(eq(INSTRUMENT), any(), anyLong())).thenReturn(candles);

        assertEquals(100, quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
    }

    @Test
    void letsTheUnderlyingSizingStandWhenThereIsNoHistory() {
        when(candleRepository.findBeforeOrEqual(eq(INSTRUMENT), any(), anyLong())).thenReturn(List.of());

        assertEquals(100_000, quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
    }

    @Test
    void asksForAFixedNumberOfBarsEndingAtTheDecision() {
        stubBars(1000);
        quantity.setLookbackCandles(50);

        quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0));

        verify(candleRepository).findBeforeOrEqual(INSTRUMENT, NOW, 50);
    }

    @Test
    void scalesTheCapWithTheShareAllowed() {
        stubBars(1000);
        quantity.setBarVolumeShare(0.5);

        assertEquals(500, quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
    }

    @Test
    void refusesSettingsThatWouldCapNothing() {
        assertThrows(IllegalArgumentException.class, () -> quantity.setBarVolumeShare(0));
        assertThrows(IllegalArgumentException.class, () -> quantity.setBarVolumeShare(1.5));
        assertThrows(IllegalArgumentException.class, () -> quantity.setLookbackCandles(0));
        assertThrows(IllegalArgumentException.class, () -> quantity.setLookbackCandles(-1));
    }

    /**
     * The two caps answer different questions - how much of a position to hold, and how much to ask
     * for at once - so stacking them leaves the tighter of the two in charge.
     */
    @Test
    void composesWithTheDailyVolumeCap() {
        stubBars(1000);

        BarVolumeCappedQuantity stacked = new BarVolumeCappedQuantity(
            new AdvCappedQuantity(new SignalScaledQuantity(), candleRepository).setDailyVolumeShare(1.0),
            candleRepository
        );

        // The daily cap is deliberately loose here, so the per-bar one decides.
        assertEquals(100, stacked.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
    }

    private void stubSplitBars(long buy, long sell) {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            candles.add(new Candle(
                INSTRUMENT,
                new TimePoint(NOW.minus(Duration.ofMinutes(i))),
                Quotation.of(6), Quotation.of(6), Quotation.of(6), Quotation.of(6),
                buy + sell, buy, sell
            ));
        }

        when(candleRepository.findBeforeOrEqual(eq(INSTRUMENT), any(), anyLong())).thenReturn(candles);
    }

    private void stubBars(long volumePerCandle) {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            candles.add(candleAt(NOW.minus(Duration.ofMinutes(i)), volumePerCandle));
        }

        when(candleRepository.findBeforeOrEqual(eq(INSTRUMENT), any(), anyLong())).thenReturn(candles);
    }

    private Candle candleAt(Instant time, long volume) {
        return new Candle(
            INSTRUMENT,
            new TimePoint(time),
            Quotation.of(6),
            Quotation.of(6),
            Quotation.of(6),
            Quotation.of(6),
            volume
        );
    }

    private TradeMoment moment(double signal) {
        return new TradeMoment(INSTRUMENT, NOW, new Upside(signal, 1.0));
    }
}
