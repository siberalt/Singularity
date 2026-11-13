package com.siberalt.singularity.strategy.signal;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Делегат - счётчик баров, которые он видел; так видно, свежий он или старый. */
class ResettingSignalSourceTest {
    private static final Signal EVENT = new Signal(SignalType.POSITION_EXIT, -1, 1, 1);

    private Signal triggered = Signal.NEUTRAL;

    private final List<int[]> made = new ArrayList<>();

    private final ResettingSignalSource source = new ResettingSignalSource(candles -> triggered, () -> {
        int[] seen = new int[1];

        made.add(seen);

        return candles -> new Signal(1, ++seen[0]);
    });

    /** Первый делегат есть сразу, и без событий отвечает он. */
    @Test
    void delegatesWhileNothingHappens() {
        assertEquals(1, made.size());
        assertEquals(1, next().strength());
        assertEquals(2, next().strength());
    }

    /** На событии отвечает триггер, делегат - свежий, и начинает он со следующего бара. */
    @Test
    void startsAFreshDelegateOnAnEvent() {
        next();
        next();

        triggered = EVENT;
        assertSame(EVENT, next());
        assertEquals(2, made.size());
        assertEquals(2, made.get(0)[0], "старый делегат бар события не видел");
        assertEquals(0, made.get(1)[0], "новый - тоже");

        triggered = Signal.NEUTRAL;
        assertEquals(1, next().strength());
    }

    /** Измеренный ноль - не молчание, а событие. */
    @Test
    void takesAMeasuredZeroAsAnEvent() {
        triggered = new Signal(0, 0);

        assertEquals(SignalType.UNSPECIFIED, next().type());
        assertEquals(2, made.size());
    }

    /**
     * Сборка целиком: смена тренда закрывает и пересоздаёт фиксеры, и лонговый, вернувшийся через два тренда,
     * входит заново, а не откупает снятое в прошлом лонге.
     */
    @Test
    void routesFixersByTrendAndForgetsTheLastTrend() {
        double[] trend = {1};
        Signal[] said = {Signal.NEUTRAL};
        SignalSource base = candles -> said[0];
        Signal buy = new Signal(SignalType.POSITION_ENTRY, 1, 1, Signal.UNDEFINED_SHARE);
        Signal sell = new Signal(SignalType.POSITION_EXIT, -1, 1, Signal.UNDEFINED_SHARE);
        Signal shortEntry = new Signal(SignalType.POSITION_ENTRY, -1, 1, Signal.UNDEFINED_SHARE);
        ResettingSignalSource cycle = new ResettingSignalSource(
            new RegimeChangeSignalSource(candles -> trend[0]),
            () -> new RangeSwitchSignalSource(candles -> trend[0], List.of(
                RangeSwitchSignalSource.Branch.below(0, new PartialExitSignalSource(base, -1, 0.9, 0.9, 0.5)),
                RangeSwitchSignalSource.Branch.from(0, new PartialExitSignalSource(base, 1, 0.9, 0.9, 0.5)))));

        assertSame(buy, at(cycle, said, buy));
        assertEquals(0.5, at(cycle, said, sell).share(), "лонг снял половину");

        trend[0] = -1;
        assertEquals(SignalType.POSITION_EXIT, at(cycle, said, Signal.NEUTRAL).type());
        assertSame(shortEntry, at(cycle, said, shortEntry), "шорт ведёт свой фиксер");

        trend[0] = 1;
        assertEquals(1, at(cycle, said, Signal.NEUTRAL).confidence(), "смена закрывает шорт");
        assertSame(buy, at(cycle, said, buy), "новый лонг - вход, а не откуп прошлой половины");
    }

    @Test
    void needsATriggerAndSomethingToMake() {
        SignalSource any = candles -> Signal.NEUTRAL;

        assertThrows(IllegalArgumentException.class, () -> new ResettingSignalSource(null, () -> any));
        assertThrows(IllegalArgumentException.class, () -> new ResettingSignalSource(any, null));
        assertThrows(IllegalStateException.class, () -> new ResettingSignalSource(any, () -> null));
    }

    private static Signal at(SignalSource source, Signal[] said, Signal signal) {
        said[0] = signal;

        return source.calculate(List.of());
    }

    private Signal next() {
        return source.calculate(List.of());
    }
}
