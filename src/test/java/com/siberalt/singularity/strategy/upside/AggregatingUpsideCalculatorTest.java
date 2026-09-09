package com.siberalt.singularity.strategy.upside;

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

class AggregatingUpsideCalculatorTest {
    private static final Instant HOUR_START = Instant.parse("2021-06-03T10:00:00Z");

    private List<List<Candle>> seenByDelegate;
    private AggregatingUpsideCalculator calculator;

    @BeforeEach
    void setUp() {
        seenByDelegate = new ArrayList<>();
        calculator = new AggregatingUpsideCalculator(
            CandleInterval.HOUR,
            candles -> {
                seenByDelegate.add(List.copyOf(candles));

                return new Upside(seenByDelegate.size(), 1);
            }
        );
    }

    /**
     * The hour in progress is nobody's business yet: its high, low and volume are still moving, and
     * a strategy asking every minute would otherwise get sixty answers to one question.
     */
    @Test
    void saysNothingUntilTheIntervalCloses() {
        assertEquals(Upside.NEUTRAL, calculator.calculate(List.of(minute(0, 10, 11))));
        assertEquals(Upside.NEUTRAL, calculator.calculate(List.of(minute(30, 11, 12))));
        assertEquals(List.of(), seenByDelegate);
    }

    @Test
    void passesOnOneClosedBarPerInterval() {
        calculator.calculate(List.of(minute(0, 10, 11)));
        calculator.calculate(List.of(minute(30, 11, 14)));

        // The first candle of the next hour is what tells us the previous one is complete.
        Upside upside = calculator.calculate(List.of(minute(60, 14, 15)));

        assertEquals(1, seenByDelegate.size());
        assertEquals(1, upside.signal());

        Candle hour = seenByDelegate.getFirst().getFirst();
        assertEquals(Quotation.of(10), hour.open());
        assertEquals(Quotation.of(14), hour.close());
        assertEquals(HOUR_START, hour.getTime());
    }

    /**
     * The first call carries the whole lookback a strategy starts with, which is many hours at once.
     */
    @Test
    void feedsEveryClosedIntervalOfALongerRunInOrder() {
        List<Candle> threeHours = new ArrayList<>();

        for (int minute = 0; minute < 180; minute += 30) {
            threeHours.add(minute(minute, 10 + minute, 10 + minute));
        }

        Upside upside = calculator.calculate(threeHours);

        // Two hours closed; the third is still open.
        assertEquals(2, seenByDelegate.size());
        assertEquals(2, upside.signal());
        assertEquals(HOUR_START, seenByDelegate.getFirst().getFirst().getTime());
        assertEquals(HOUR_START.plusSeconds(3600), seenByDelegate.getLast().getFirst().getTime());
    }

    @Test
    void carriesTheOpenBarAcrossCalls() {
        calculator.calculate(List.of(minute(0, 10, 11)));
        calculator.calculate(List.of(minute(59, 11, 20)));
        calculator.calculate(List.of(minute(60, 20, 21)));

        assertEquals(Quotation.of(10), seenByDelegate.getFirst().getFirst().open());
        assertEquals(Quotation.of(20), seenByDelegate.getFirst().getFirst().close());
    }

    private Candle minute(int offset, double open, double close) {
        return new Candle(
            "TEST",
            new TimePoint(HOUR_START.plusSeconds(offset * 60L)),
            Quotation.of(open),
            Quotation.of(close),
            Quotation.of(Math.max(open, close)),
            Quotation.of(Math.min(open, close)),
            100
        );
    }
}
