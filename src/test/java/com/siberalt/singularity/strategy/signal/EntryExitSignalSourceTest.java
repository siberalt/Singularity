package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryExitSignalSourceTest {
    private static final List<Candle> ANY_CANDLES = List.of(
        Candle.of(TimePoint.NULL, 100, 100, 100, 100, 100)
    );

    /** Says what it was told to, bar by bar, and remembers every time it was asked. */
    private static class Scripted implements SignalSource {
        private final List<Signal> script;

        private int asked;

        Scripted(Signal... script) {
            this.script = List.of(script);
        }

        @Override
        public Signal calculate(List<Candle> lastCandles) {
            Signal signal = script.get(Math.min(asked, script.size() - 1));

            asked++;

            return signal;
        }
    }

    private Signal of(double signal) {
        return new Signal(signal, 1);
    }

    /** Закрывающий сигнал: его обёртка называет типом, и этим он отличается от входа в другую сторону. */
    private Signal closing(double signal) {
        return new Signal(SignalType.POSITION_EXIT, signal, 1, Signal.UNDEFINED_BALANCE);
    }

    private List<Signal> run(EntryExitSignalSource calculator, int bars) {
        List<Signal> answers = new ArrayList<>();

        for (int bar = 0; bar < bars; bar++) {
            answers.add(calculator.calculate(ANY_CANDLES));
        }

        return answers;
    }

    @Test
    void should_PassTheEntrySignalThrough_WhenItFires() {
        var calculator = new EntryExitSignalSource(new Scripted(new Signal(0.8, 0.5)),
            new Scripted(Signal.NEUTRAL));

        assertEquals(new Signal(0.8, 0.5), calculator.calculate(ANY_CANDLES));
    }

    @Test
    void should_CloseAtFullConfidence_WhenTheExitPointsTheOtherWay() {
        var calculator = new EntryExitSignalSource(new Scripted(of(1)),
            new Scripted(Signal.NEUTRAL, new Signal(-0.3, 0.1)));

        assertEquals(List.of(of(1), Signal.NEUTRAL, closing(-1)), run(calculator, 3));
    }

    /** The mirror: a short opened by a fall is closed by a rise, and the closing signal is a buy. */
    @Test
    void should_CloseAShort_WhenTheExitPointsUp() {
        var calculator = new EntryExitSignalSource(new Scripted(of(-1)),
            new Scripted(of(1)));

        assertEquals(List.of(of(-1), closing(1)), run(calculator, 2));
    }

    @Test
    void should_KeepWaiting_WhenTheExitPointsTheSameWay() {
        var calculator = new EntryExitSignalSource(new Scripted(of(1)),
            new Scripted(of(1), of(1), of(-1)));

        assertEquals(List.of(of(1), Signal.NEUTRAL, Signal.NEUTRAL, closing(-1)), run(calculator, 4));
    }

    @Test
    void should_NotAskTheExit_OnTheBarTheEntryFired() {
        var exit = new Scripted(of(-1));
        var calculator = new EntryExitSignalSource(new Scripted(of(1)), exit);

        calculator.calculate(ANY_CANDLES);

        assertEquals(0, exit.asked);
    }

    @Test
    void should_NotAskTheEntry_WhileThePositionIsOpen() {
        var entry = new Scripted(of(1));
        var calculator = new EntryExitSignalSource(entry, new Scripted(Signal.NEUTRAL));

        run(calculator, 5);

        assertEquals(1, entry.asked);
    }

    @Test
    void should_ListenToTheEntryAgain_AfterThePositionIsClosed() {
        var calculator = new EntryExitSignalSource(new Scripted(of(1)), new Scripted(of(-1)));

        assertEquals(List.of(of(1), closing(-1), of(1), closing(-1)), run(calculator, 4));
    }

    @Test
    void should_HoldForever_WhenTheExitNeverSpeaks() {
        var calculator = new EntryExitSignalSource(new Scripted(of(1)),
            new Scripted(Signal.NEUTRAL));
        List<Signal> answers = run(calculator, 100);

        assertEquals(of(1), answers.getFirst());
        assertTrue(answers.subList(1, answers.size()).stream().allMatch(Signal.NEUTRAL::equals));
    }

    /** A delegate that builds its own object for every bar still has to be recognised as silent. */
    @Test
    void should_TreatAZeroSignalAsSilence_EvenWhenItIsNotTheConstant() {
        var calculator = new EntryExitSignalSource(new Scripted(new Signal(0, 1)),
            new Scripted(of(-1)));

        assertEquals(List.of(Signal.NEUTRAL, Signal.NEUTRAL), run(calculator, 2));
    }

    @Test
    void should_IgnoreSignalsWeakerThanTheMinimum_OnBothSides() {
        var calculator = new EntryExitSignalSource(new Scripted(new Signal(0.4, 1), new Signal(0.9, 1)),
            new Scripted(new Signal(-0.4, 1), new Signal(-0.9, 1)), 0.5);

        assertEquals(List.of(Signal.NEUTRAL, new Signal(0.9, 1), Signal.NEUTRAL, closing(-1)),
            run(calculator, 4));
    }

    @Test
    void should_CloseItself_WhenTheExitStaysSilentPastTheLimit() {
        var calculator = new EntryExitSignalSource(new Scripted(of(1)),
            new Scripted(Signal.NEUTRAL)).setMaxWaitBars(3);

        assertEquals(List.of(of(1), Signal.NEUTRAL, Signal.NEUTRAL, closing(-1)), run(calculator, 4));
    }

    @Test
    void should_CloseOnTheExit_WhenItSpeaksBeforeTheLimit() {
        var calculator = new EntryExitSignalSource(new Scripted(of(1)),
            new Scripted(Signal.NEUTRAL, of(-1))).setMaxWaitBars(10);

        assertEquals(List.of(of(1), Signal.NEUTRAL, closing(-1)), run(calculator, 3));
    }

    /** The limit is a limit on one position, not on the calculator's life. */
    @Test
    void should_CountTheWaitAfresh_ForEachPosition() {
        var calculator = new EntryExitSignalSource(new Scripted(of(1)),
            new Scripted(Signal.NEUTRAL)).setMaxWaitBars(2);

        assertEquals(List.of(of(1), Signal.NEUTRAL, closing(-1), of(1), Signal.NEUTRAL, closing(-1)),
            run(calculator, 6));
    }

    @Test
    void should_CloseOnTheFirstBarAfterTheEntry_WhenTheLimitIsOneBar() {
        var calculator = new EntryExitSignalSource(new Scripted(of(-1)),
            new Scripted(Signal.NEUTRAL)).setMaxWaitBars(1);

        assertEquals(List.of(of(-1), closing(1)), run(calculator, 2));
    }

    @Test
    void should_Throw_WhenTheWaitIsShorterThanABar() {
        assertThrows(IllegalArgumentException.class,
            () -> new EntryExitSignalSource(new Scripted(of(1)), new Scripted(of(-1)))
                .setMaxWaitBars(0));
    }

    @Test
    void should_Throw_WhenADelegateIsMissing() {
        assertThrows(IllegalArgumentException.class,
            () -> new EntryExitSignalSource(null, new Scripted(of(1))));
        assertThrows(IllegalArgumentException.class,
            () -> new EntryExitSignalSource(new Scripted(of(1)), null));
    }

    @Test
    void should_Throw_WhenTheMinimumSignalIsNotPositive() {
        assertThrows(IllegalArgumentException.class,
            () -> new EntryExitSignalSource(new Scripted(of(1)), new Scripted(of(-1)), 0));
        assertThrows(IllegalArgumentException.class,
            () -> new EntryExitSignalSource(new Scripted(of(1)), new Scripted(of(-1)), -1));
    }
}
