package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.siberalt.singularity.strategy.market.TrendZone.DOWN;
import static com.siberalt.singularity.strategy.market.TrendZone.UP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Тренд и наклон подаются по сценарию, бар за баром; свечи источнику не нужны. */
class TrendCycleSignalSourceTest {
    private final List<Double> trends = new ArrayList<>();

    private final List<Double> slopes = new ArrayList<>();

    private int bar;

    private final TrendCycleSignalSource source = new TrendCycleSignalSource(
        candles -> trends.get(bar),
        candles -> new Signal(slopes.get(bar), 1),
        new TrendCycle(0.3, true)
    );

    @Test
    void saysNothingUntilTheTargetFirstMoves() {
        assertSame(Signal.NEUTRAL, next(UP, 0.1));
    }

    @Test
    void namesTheTargetAndBuysTowardsIt() {
        Signal entry = next(UP, 0.5);

        assertEquals(1, entry.confidence());
        assertEquals(1, entry.positionBalance());
        assertEquals(SignalType.POSITION_ENTRY, entry.type());
    }

    /**
     * Цель не менялась - а сторона прежняя: недобранная позиция добирается, набранная ничего не просит.
     * Скажи источник на этом баре «нейтрально», стратегия не спросила бы разность до цели вовсе.
     */
    @Test
    void keepsTheSideOfTheLastChangeWhileTheTargetHolds() {
        next(UP, 0.5);

        Signal held = next(UP, -0.9);

        assertEquals(1, held.confidence());
        assertEquals(1, held.positionBalance());
    }

    /** Закрытие лонга на смене тренда - продажа к нулю, и это выход. */
    @Test
    void sellsToMoneyWhenTheTrendTurns() {
        next(UP, 0.5);

        Signal exit = next(DOWN, -0.1);

        assertEquals(-1, exit.confidence());
        assertEquals(0, exit.positionBalance());
        assertEquals(SignalType.POSITION_EXIT, exit.type());
    }

    /** Разворот в шорт одним баром - продажа, и это вход: позиция сменила сторону. */
    @Test
    void sellsIntoAShortWhenTheTurnIsConfirmedAtOnce() {
        next(UP, 0.5);

        Signal reversal = next(DOWN, -0.5);

        assertEquals(-1, reversal.confidence());
        assertEquals(-1, reversal.positionBalance());
        assertEquals(SignalType.POSITION_ENTRY, reversal.type());
    }

    /** Закрытие шорта - покупка к нулю. */
    @Test
    void buysBackTheShortWhenTheTrendIsLost() {
        next(DOWN, -0.5);

        Signal cover = next(Double.NaN, 0);

        assertEquals(1, cover.confidence());
        assertEquals(0, cover.positionBalance());
        assertEquals(SignalType.POSITION_EXIT, cover.type());
    }

    @Test
    void refusesMissingParts() {
        assertThrows(IllegalArgumentException.class,
            () -> new TrendCycleSignalSource(null, candles -> Signal.NEUTRAL, new TrendCycle(0.3, true)));
        assertThrows(IllegalArgumentException.class,
            () -> new TrendCycleSignalSource(candles -> UP, null, new TrendCycle(0.3, true)));
        assertThrows(IllegalArgumentException.class,
            () -> new TrendCycleSignalSource(candles -> UP, candles -> Signal.NEUTRAL, null));
    }

    private Signal next(double trend, double slope) {
        trends.add(trend);
        slopes.add(slope);

        Signal signal = source.calculate(List.<Candle>of());

        bar++;

        return signal;
    }
}
