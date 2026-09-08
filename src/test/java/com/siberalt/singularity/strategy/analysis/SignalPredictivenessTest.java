package com.siberalt.singularity.strategy.analysis;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignalPredictivenessTest {
    private static final Instant START = Instant.parse("2021-06-03T10:00:00Z");
    private static final double STEP = 0.01;

    /** Reads the last candle's own body, which is all these tests need a calculator to do. */
    private static final UpsideCalculator BODY_SIGN = candles -> {
        Candle last = candles.getLast();

        return new Upside(Math.signum(last.getCloseAsDouble() - last.getOpenAsDouble()), 1);
    };

    /**
     * A series where each candle's body announces the move of the bar after next - the best a
     * signal could possibly be, given it cannot trade before the next bar opens.
     */
    @Test
    void findsASignalThatActuallyPredicts() {
        PredictivenessReport.HorizonStat stat = measure(series(true));

        assertEquals(1.0, stat.executableCorrelation(), 1e-9);
        assertEquals(100 * STEP, stat.edgeBasisPoints() / 100, 1e-9);
        assertTrue(stat.isAboveNoise());
    }

    /**
     * The whole point of the tool. Here each candle's body announces its own bar, which a strategy
     * cannot act on - it only learns of it once the bar has closed. The same-bar column, which
     * prices the trade at that bar's open, calls it perfect; the executable one sees nothing.
     */
    @Test
    void tellsALookAheadApartFromASignal() {
        PredictivenessReport.HorizonStat stat = measure(series(false));

        assertEquals(1.0, stat.sameBarCorrelation(), 1e-9);
        assertTrue(
            Math.abs(stat.executableCorrelation()) < stat.noiseFloor(),
            "a look-ahead should leave nothing behind, got " + stat.executableCorrelation()
        );
    }

    /**
     * What holding paid over the same stretch, which is what an edge has to be read against: a
     * signal that is long most of the time earns the drift of a rising market for free.
     */
    @Test
    void measuresWhatHoldingPaid() {
        List<Candle> rising = new ArrayList<>();
        double price = 100;

        for (int i = 0; i < 200; i++) {
            rising.add(candle(i, price, price * (1 + STEP)));
            price *= 1 + STEP;
        }

        PredictivenessReport report = new SignalPredictiveness()
            .setLookbackCandles(2)
            .setHorizons(1)
            .measure(rising, BODY_SIGN);

        assertEquals(100 * STEP, report.horizons().getFirst().baselineBasisPoints() / 100, 1e-9);
    }

    @Test
    void countsOnlyTheBarsWhereTheSignalFired() {
        List<Candle> flat = new ArrayList<>();

        for (int i = 0; i < 200; i++) {
            flat.add(candle(i, 100, 100));
        }

        PredictivenessReport report = new SignalPredictiveness()
            .setLookbackCandles(2)
            .setHorizons(1)
            .measure(flat, BODY_SIGN);

        assertEquals(0, report.firedBars());
        assertTrue(report.bars() > 0);
    }

    private PredictivenessReport.HorizonStat measure(List<Candle> candles) {
        return new SignalPredictiveness()
            .setLookbackCandles(2)
            .setHorizons(1)
            .measure(candles, BODY_SIGN)
            .horizons()
            .getFirst();
    }

    /**
     * @param predictive whether a candle's body announces the next bar's move (a real signal) or
     *                   its own (a look-ahead)
     */
    private List<Candle> series(boolean predictive) {
        Random random = new Random(42);
        int size = 400;
        double[] direction = new double[size + 2];

        for (int i = 0; i < direction.length; i++) {
            direction[i] = random.nextBoolean() ? 1 : -1;
        }

        double[] open = new double[size + 2];
        open[0] = 100;

        for (int i = 0; i < size + 1; i++) {
            open[i + 1] = open[i] * (1 + STEP * direction[i]);
        }

        List<Candle> candles = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            double announced = direction[predictive ? i + 1 : i];
            candles.add(candle(i, open[i], open[i] * (1 + STEP * announced)));
        }

        return candles;
    }

    private Candle candle(int index, double open, double close) {
        return new Candle(
            "TEST",
            new TimePoint(START.plusSeconds(index * 60L)),
            Quotation.of(open),
            Quotation.of(close),
            Quotation.of(Math.max(open, close)),
            Quotation.of(Math.min(open, close)),
            1000
        );
    }
}
