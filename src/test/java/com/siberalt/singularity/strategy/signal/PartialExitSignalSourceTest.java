package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Сигналы источника подаются по сценарию, бар за баром; лонговый фиксер, пороги 0.5 и 0.8, план 0.1, 0.2,
 * 0.2, откуп после плана.
 */
class PartialExitSignalSourceTest {
    private static final Signal BUY = new Signal(SignalType.POSITION_ENTRY, 1, 0.3, Signal.UNDEFINED_SHARE);

    private static final Signal SHORT = new Signal(SignalType.POSITION_ENTRY, -1, 0.3, Signal.UNDEFINED_SHARE);

    private static final Signal SELL = new Signal(SignalType.POSITION_EXIT, -0.9, 0.4, Signal.UNDEFINED_SHARE);

    private static final Signal COVER = new Signal(SignalType.POSITION_EXIT, 0.9, 0.4, Signal.UNDEFINED_SHARE);

    private final List<Signal> signals = new ArrayList<>();

    private final SignalSource scripted = candles -> signals.get(signals.size() - 1);

    private final PartialExitSignalSource source = new PartialExitSignalSource(scripted, 1, 0.5, 0.8, 0.1, 0.2, 0.2);

    /** До входа источник молчит обо всём, кроме сильного входа своей стороны. */
    @Test
    void waitsForAnEntryOnItsOwnSide() {
        assertSame(Signal.NEUTRAL, next(SELL));
        assertSame(Signal.NEUTRAL, next(SHORT));
        assertSame(Signal.NEUTRAL, next(new Signal(SignalType.POSITION_ENTRY, 0.4, 1, Signal.UNDEFINED_SHARE)));
        assertSame(BUY, next(BUY));
    }

    /** Встречный выход после входа - фиксация: тип другой, уверенность полная, доля - шаг плана. */
    @Test
    void turnsAnExitIntoATakeProfit() {
        next(BUY);

        Signal fixation = next(SELL);

        assertEquals(SignalType.TAKE_PROFIT, fixation.type());
        assertEquals(-1, fixation.confidence());
        assertEquals(0.1, fixation.share());
        assertEquals(0.4, fixation.strength());
    }

    /** Шаги идут по плану, а между ними - тишина: откуп после плана, а не посреди него. */
    @Test
    void walksThePlanStepByStep() {
        next(BUY);

        assertEquals(0.1, next(SELL).share());
        assertSame(Signal.NEUTRAL, next(BUY));
        assertSame(Signal.NEUTRAL, next(Signal.NEUTRAL));
        assertEquals(0.2, next(SELL).share());
        assertEquals(0.2, next(SELL).share());
    }

    /**
     * План выполнен - фиксаций больше нет, ждёт откупа. Откуп - ровно снятое, и с него цикл заново: следующая
     * фиксация - снова первый шаг.
     */
    @Test
    void buysBackWhatItTookAndStartsOver() {
        next(BUY);
        next(SELL);
        next(SELL);
        next(SELL);

        assertSame(Signal.NEUTRAL, next(SELL));

        Signal buyBack = next(BUY);

        assertEquals(SignalType.POSITION_ENTRY, buyBack.type());
        assertEquals(1, buyBack.confidence());
        assertEquals(0.5, buyBack.share(), 1e-12);
        assertEquals(0.1, next(SELL).share());
    }

    /** Фиксация - только против позиции, только выходом и только по своему порогу. */
    @Test
    void catchesOnlyStrongExitsAgainstThePosition() {
        next(BUY);

        assertSame(Signal.NEUTRAL, next(COVER));
        assertSame(Signal.NEUTRAL, next(new Signal(SignalType.POSITION_EXIT, -0.7, 1, Signal.UNDEFINED_SHARE)));
        assertSame(Signal.NEUTRAL, next(SHORT));
        assertEquals(SignalType.TAKE_PROFIT, next(SELL).type());
    }

    /** Шортовый фиксер: вход - продажа, фиксация - откуп части, откуп снятого - снова продажа. */
    @Test
    void leadsAShort() {
        PartialExitSignalSource shorts = new PartialExitSignalSource(scripted, -1, 0.5, 0.8, 0.5);

        assertSame(Signal.NEUTRAL, next(shorts, BUY));
        assertSame(SHORT, next(shorts, SHORT));

        Signal fixation = next(shorts, COVER);

        assertEquals(1, fixation.confidence());
        assertEquals(0.5, fixation.share());

        Signal buyBack = next(shorts, SHORT);

        assertEquals(-1, buyBack.confidence());
        assertEquals(0.5, buyBack.share());
    }

    /** Источнику, который знает только направление, роль задаёт фиксер. */
    @Test
    void readsBareDirectionAsEitherRole() {
        Signal up = new Signal(1, 0.5);
        Signal down = new Signal(-1, 0.5);
        PartialExitSignalSource once = new PartialExitSignalSource(scripted, 1, 0.5, 0.8, 0.3);

        assertSame(up, next(once, up));
        assertSame(Signal.NEUTRAL, next(once, up));
        assertEquals(SignalType.TAKE_PROFIT, next(once, down).type());
        assertEquals(SignalType.POSITION_ENTRY, next(once, up).type());
    }

    /** Откупать нечего, пока ничего не снято, - что бы ни говорило правило. */
    @Test
    void buysNothingBackWhileNothingIsTaken() {
        PartialExitSignalSource eager = fixer(new StepTakePlan(0.5), (progress, done, candles) -> true);

        next(eager, BUY);

        assertSame(Signal.NEUTRAL, next(eager, BUY));
    }

    /** Когда откупать, решает правило, и фиксер отдаёт ему позицию и то, выполнен ли план. */
    @Test
    void asksTheRuleWhenToBuyBack() {
        List<String> asked = new ArrayList<>();
        boolean[] allow = {false};
        PartialExitSignalSource ruled = fixer(new StepTakePlan(0.1, 0.2, 0.2), (progress, done, candles) -> {
            asked.add(progress + " " + done);

            return allow[0];
        });

        next(ruled, BUY);
        next(ruled, SELL);

        assertSame(Signal.NEUTRAL, next(ruled, BUY));

        allow[0] = true;

        assertEquals(0.1, next(ruled, BUY).share());
        assertEquals(List.of("TakeProgress[steps=1, taken=0.1, side=1.0] false",
            "TakeProgress[steps=1, taken=0.1, side=1.0] false"), asked);
        assertEquals(0.1, next(ruled, SELL).share(), "откуп начал план заново");
    }

    /**
     * Сколько снимать - решает план, и фиксер отдаёт ему позицию. Выход, на котором план назвал ноль, - не
     * фиксация: тишина, и шаг не сдвигается. План выполнен - фиксаций нет, пока не откуплено.
     */
    @Test
    void asksThePlanHowMuchToTake() {
        List<TakeProgress> asked = new ArrayList<>();
        double[] answers = {0.3, 0, 0.2};
        TakePlan plan = new TakePlan() {
            @Override
            public double shareOf(TakeProgress progress, List<Candle> lastCandles) {
                asked.add(progress);

                return answers[asked.size() - 1];
            }

            @Override
            public boolean done(TakeProgress progress) {
                return progress.taken() >= 0.5 - 1e-12;
            }
        };
        PartialExitSignalSource planned = fixer(plan, ReentryRule.afterPlan());

        next(planned, BUY);

        assertEquals(0.3, next(planned, SELL).share());
        assertSame(Signal.NEUTRAL, next(planned, SELL));
        assertEquals(0.2, next(planned, SELL).share());
        assertEquals(List.of(new TakeProgress(0, 0, 1), new TakeProgress(1, 0.3, 1), new TakeProgress(1, 0.3, 1)),
            asked);
        assertSame(Signal.NEUTRAL, next(planned, SELL));
        assertEquals(3, asked.size(), "выполненный план больше не спрашивают");
    }

    /** Доля вне [0, 1] или больше остатка счёта - ошибка плана, а не сделка. */
    @Test
    void refusesAShareThePlanCannotHave() {
        PartialExitSignalSource tooMuch = fixer(new TakePlan() {
            @Override
            public double shareOf(TakeProgress progress, List<Candle> lastCandles) {
                return 0.6;
            }

            @Override
            public boolean done(TakeProgress progress) {
                return false;
            }
        }, ReentryRule.afterPlan());

        next(tooMuch, BUY);
        next(tooMuch, SELL);

        assertThrows(IllegalStateException.class, () -> next(tooMuch, SELL));
    }

    @Test
    void refusesWhatItCannotKeep() {
        SignalSource any = candles -> Signal.NEUTRAL;
        TakePlan plan = new StepTakePlan(0.1);
        ReentryRule rule = ReentryRule.afterPlan();

        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(null, 1, 0.5, 0.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 0, 0.5, 0.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 0.5, 0.5, 0.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 1, 0, 0.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 1, 0.5, 1.5, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 1, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 1, 0.5, 0.5, 0.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 1, 0.5, 0.5, 0.6, 0.6));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 1, 0.5, 0.5, null, rule));
        assertThrows(IllegalArgumentException.class, () -> new PartialExitSignalSource(any, 1, 0.5, 0.5, plan, null));
    }

    private PartialExitSignalSource fixer(TakePlan plan, ReentryRule rule) {
        return new PartialExitSignalSource(scripted, 1, 0.5, 0.8, plan, rule);
    }

    private Signal next(Signal signal) {
        return next(source, signal);
    }

    private Signal next(PartialExitSignalSource fixer, Signal signal) {
        signals.add(signal);

        return fixer.calculate(List.of());
    }
}
