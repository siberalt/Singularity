package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Дневные бары из часовых: мера-шпион записывает, что ей дали, и отвечает закрытием последнего бара.
 */
class ClosedBarsCoefficientTest {
    private static final Instant DAY_START = Instant.parse("2024-03-04T00:00:00Z");

    private List<List<Candle>> seen;

    private ClosedBarsCoefficient daily;

    @BeforeEach
    void setUp() {
        seen = new ArrayList<>();
        daily = new ClosedBarsCoefficient(CandleInterval.DAY, 3, spy());
    }

    /** День ещё идёт - его закрытие пока не закрытие, читать нечего. */
    @Test
    void saysNothingWhileTheFirstDayIsOpen() {
        assertTrue(Double.isNaN(daily.of(List.of(hour(0, 7, 100), hour(0, 8, 101)))));
        assertEquals(List.of(), seen);
    }

    /** Первый час следующего дня закрывает предыдущий: мера видит его целиком, и только его. */
    @Test
    void readsADayOnceTheNextOneStarts() {
        daily.of(List.of(hour(0, 7, 100), hour(0, 12, 104), hour(0, 20, 103)));

        assertEquals(103, daily.of(List.of(hour(1, 7, 90))));
        assertEquals(1, seen.size());

        Candle day = seen.getFirst().getFirst();
        assertEquals(Quotation.of(100), day.open());
        assertEquals(Quotation.of(103), day.close());
        assertEquals(Quotation.of(104), day.high());
    }

    /**
     * Свечи идущего дня мера не видит, как бы далеко он ни зашёл: обвал до 50 к полудню не меняет чтения,
     * пока день не закрыт. И мера не пересчитывается - чтение прежнее.
     */
    @Test
    void neverShowsTheDayInProgress() {
        daily.of(List.of(hour(0, 7, 100), hour(1, 7, 101)));
        daily.of(List.of(hour(1, 10, 70)));

        assertEquals(100, daily.of(List.of(hour(1, 12, 50))));
        assertEquals(1, seen.size());
    }

    /** Окно стратегии повторяет уже показанное: каждый день строится один раз. */
    @Test
    void countsEachCandleOnceHoweverOftenItIsShown() {
        List<Candle> hours = List.of(hour(0, 7, 100), hour(0, 8, 101), hour(1, 7, 102), hour(1, 8, 103),
            hour(2, 7, 104));

        for (int shown = 1; shown <= hours.size(); shown++) {
            daily.of(hours.subList(0, shown));
        }

        assertEquals(2, seen.size());
        assertEquals(2, seen.getLast().size());
        assertEquals(Quotation.of(103), seen.getLast().getLast().close());
    }

    /** Начальная история - много дней разом: мера читает их один раз, в окне не больше трёх последних. */
    @Test
    void keepsTheLastWindowOfClosedBars() {
        List<Candle> history = new ArrayList<>();

        for (int day = 0; day < 6; day++) {
            history.add(hour(day, 7, 100 + day));
        }

        assertEquals(104, daily.of(history));
        assertEquals(1, seen.size());
        assertEquals(List.of(102.0, 103.0, 104.0),
            seen.getFirst().stream().map(Candle::getCloseAsDouble).toList());
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class, () -> new ClosedBarsCoefficient(null, 3, spy()));
        assertThrows(IllegalArgumentException.class,
            () -> new ClosedBarsCoefficient(CandleInterval.MONTH, 3, spy()));
        assertThrows(IllegalArgumentException.class, () -> new ClosedBarsCoefficient(CandleInterval.DAY, 0, spy()));
        assertThrows(IllegalArgumentException.class, () -> new ClosedBarsCoefficient(CandleInterval.DAY, 3, null));
    }

    private MarketCoefficient spy() {
        return candles -> {
            seen.add(List.copyOf(candles));

            return candles.getLast().getCloseAsDouble();
        };
    }

    private static Candle hour(int day, int hour, double close) {
        return new Candle(
            1L,
            new TimePoint(DAY_START.plusSeconds(86400L * day + 3600L * hour)),
            Quotation.of(close),
            Quotation.of(close),
            Quotation.of(close),
            Quotation.of(close),
            100
        );
    }
}
