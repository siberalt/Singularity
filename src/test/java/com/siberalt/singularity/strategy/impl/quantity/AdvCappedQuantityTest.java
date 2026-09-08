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

class AdvCappedQuantityTest {
    private static final String INSTRUMENT = "TMOS";
    private static final Instant NOW = Instant.parse("2021-06-03T10:00:00Z");

    private ReadCandleRepository candleRepository;
    private AdvCappedQuantity quantity;

    @BeforeEach
    void setUp() {
        candleRepository = mock(ReadCandleRepository.class);
        quantity = new AdvCappedQuantity(new SignalScaledQuantity(), candleRepository);
    }

    /**
     * The whole point: an order sized against the balance can be many hours of a thin instrument's
     * volume. Held down to a share of what it actually trades in a day, it becomes reachable.
     */
    @Test
    void holdsAnOrderDownToItsShareOfDailyVolume() {
        // Two trading days of 2 000 lots each, so ADV is 2 000 and a twentieth of it is 100.
        stubLookback(twoDaysOf(1000));

        assertEquals(100, quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
        assertEquals(100, quantity.toSell(moment(-1.0), TradeCapacity.of(0, 100_000)));
    }

    @Test
    void leavesASizeThatIsAlreadySmallEnoughAlone() {
        stubLookback(twoDaysOf(1000));

        // Forty is under the cap of a hundred, so the underlying sizing keeps its answer.
        assertEquals(40, quantity.toBuy(moment(1.0), TradeCapacity.of(40, 0)));
    }

    /**
     * The underlying sizing keeps its say, including its right to ask for nothing - a cap only ever
     * says "not more than", never "at least".
     */
    @Test
    void neverTurnsNothingIntoSomething() {
        stubLookback(twoDaysOf(1000));

        assertEquals(0, quantity.toBuy(moment(1.0), TradeCapacity.of(0, 0)));
        assertEquals(0, quantity.toSell(moment(-1.0), TradeCapacity.of(0, 0)));
    }

    /**
     * Divided by the days the bars actually fall on, not by the days the span covers. A holiday in
     * the middle of the lookback must not make the instrument look quieter than it is.
     */
    @Test
    void dividesByTheDaysTheBarsFallOnRatherThanTheDaysTheSpanCovers() {
        List<Candle> candles = new ArrayList<>();
        // Two trading days a fortnight apart - everything between them is holiday as far as this
        // instrument is concerned, and none of it may dilute the average.
        candles.addAll(dayOf(Instant.parse("2021-05-20T10:00:00Z"), 2, 1000));
        candles.addAll(dayOf(Instant.parse("2021-06-03T10:00:00Z"), 2, 1000));
        stubLookback(candles);

        // 4 000 lots over 2 trading days is an ADV of 2 000, a twentieth of which is 100.
        assertEquals(100, quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
    }

    /**
     * Nothing to judge by is not a licence to trade any size, but it is not a reason to refuse
     * either - the underlying sizing stands, and the simulation's own liquidity model still has the
     * last word on what fills.
     */
    @Test
    void letsTheUnderlyingSizingStandWhenThereIsNoHistory() {
        stubLookback(List.of());

        assertEquals(100_000, quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
    }

    /**
     * The lookback is a count of bars, not a span of dates: a span would sample a different amount
     * of trading depending on where the holidays fell, and the cap would drift with the calendar.
     */
    @Test
    void asksForAFixedNumberOfBarsEndingAtTheDecision() {
        stubLookback(twoDaysOf(1000));
        quantity.setLookbackCandles(500);

        quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0));

        // Ending at the moment being decided on - bars from after it would be reading the future.
        verify(candleRepository).findBeforeOrEqual(INSTRUMENT, NOW, 500);
    }

    @Test
    void scalesTheCapWithTheShareAllowed() {
        stubLookback(twoDaysOf(1000));
        quantity.setDailyVolumeShare(0.5);

        // Half of an ADV of 2 000.
        assertEquals(1000, quantity.toBuy(moment(1.0), TradeCapacity.of(100_000, 0)));
    }

    @Test
    void refusesSettingsThatWouldCapNothing() {
        assertThrows(IllegalArgumentException.class, () -> quantity.setDailyVolumeShare(0));
        assertThrows(IllegalArgumentException.class, () -> quantity.setDailyVolumeShare(1.5));
        assertThrows(IllegalArgumentException.class, () -> quantity.setLookbackCandles(0));
        assertThrows(IllegalArgumentException.class, () -> quantity.setLookbackCandles(-1));
    }

    private void stubLookback(List<Candle> candles) {
        when(candleRepository.findBeforeOrEqual(eq(INSTRUMENT), any(), anyLong())).thenReturn(candles);
    }

    private List<Candle> twoDaysOf(long volumePerCandle) {
        List<Candle> candles = new ArrayList<>(dayOf(NOW, 2, volumePerCandle));
        candles.addAll(dayOf(NOW.minus(Duration.ofDays(1)), 2, volumePerCandle));

        return candles;
    }

    private List<Candle> dayOf(Instant day, int candleCount, long volumePerCandle) {
        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < candleCount; i++) {
            candles.add(candleAt(day.plus(Duration.ofMinutes(i)), volumePerCandle));
        }

        return candles;
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
