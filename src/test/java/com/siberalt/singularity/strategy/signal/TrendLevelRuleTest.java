package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.market.MarketCoefficient;
import com.siberalt.singularity.strategy.market.TolerantLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Трендовая ветка: вход на событии пересечения, выход по уровню, терпимому к нерешительному пробою. */
class TrendLevelRuleTest {
    private static final int BARS = 10;

    /**
     * Вход срабатывает на смене стороны, а не внутри фазы. Первая сторона запоминается молча: на первом
     * баре фаза уже идёт, и считать её сменой значит входить в идущий тренд - то, от чего вход на событии
     * и отличается.
     */
    @Test
    void entersOnTheCrossRatherThanInsideThePhase() {
        double[] side = {1};
        SignalSource rule = TrendLevelRule.trendBranch(candles -> new Signal(side[0], 1),
            candles -> 0, new NullSignalSource());
        List<Candle> flat = series(100, 0, 1);

        assertEquals(Signal.NEUTRAL, rule.calculate(flat), "первая сторона - не смена");
        assertEquals(Signal.NEUTRAL, rule.calculate(flat), "та же сторона - не смена");

        side[0] = -1;

        assertEquals(-1, rule.calculate(flat).confidence(), "смена стороны - вход");
    }

    /**
     * Выше уровня обычный выход не проходит, а фиксация прибыли проходит. Это и просили; цена этого
     * известна - вето, живущее месяцами, в этой базе измерено в 51 пункт на RSI.
     */
    @Test
    void holdsThroughOrdinaryExitsAboveTheLevelButLetsProfitTakingOut() {
        List<Candle> above = series(100, 0, 1);

        assertEquals(Signal.NEUTRAL,
            opened(exit(SignalType.POSITION_EXIT), candles -> 0).calculate(above),
            "обычный выход выше уровня не проходит");
        assertEquals(SignalType.POSITION_EXIT,
            opened(exit(SignalType.TAKE_PROFIT), candles -> 0).calculate(above).type(),
            "фиксация прибыли проходит");
    }

    /** Ниже уровня проходит любой выход - асимметрия и есть смысл правила. */
    @Test
    void letsAnyExitOutBelowTheLevel() {
        assertEquals(SignalType.POSITION_EXIT,
            opened(exit(SignalType.POSITION_EXIT), candles -> 1000).calculate(series(100, 0, 1)).type());
    }

    /**
     * Терпимость в действии, и она же причина, по которой здесь один уровень вместо пары «под уровнем и
     * круто». Дрейф сквозь уровень позицию не закрывает - порог опущен в зону поддержки; решительное
     * падение закрывает - на резком ходу порог остаётся у самого уровня.
     * <p>
     * У прежней пары условий была дырка, которой здесь нет: медленный дрейф, ушедший глубоко вниз, не
     * выходил никогда, потому что круто он так и не падал. Здесь он выйдет, дойдя до зоны.
     */
    @Test
    void holdsThroughADriftButNotThroughADecisiveBreak() {
        assertEquals(Signal.NEUTRAL,
            opened(new NullSignalSource(), tolerant()).calculate(drifting()),
            "дрейф сквозь уровень - держим, порог опущен в зону");
        assertEquals(SignalType.POSITION_EXIT,
            opened(new NullSignalSource(), tolerant()).calculate(series(118, -2, 1)).type(),
            "решительное падение - выходим у самого уровня");
        assertEquals(SignalType.POSITION_EXIT,
            opened(new NullSignalSource(), tolerant()).calculate(drifting(90)).type(),
            "дрейф, дошедший до зоны поддержки, закрывает - прежняя пара условий этого не умела");
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        SignalSource trend = candles -> new Signal(1, 1);

        assertThrows(IllegalArgumentException.class,
            () -> TrendLevelRule.trendBranch(null, candles -> 0, new NullSignalSource()));
        assertThrows(IllegalArgumentException.class,
            () -> TrendLevelRule.trendBranch(trend, null, new NullSignalSource()));
        assertThrows(IllegalArgumentException.class,
            () -> TrendLevelRule.trendBranch(trend, candles -> 0, null));
    }

    /** Уровень 100 с терпимостью в пять процентов: дрейф терпится до 95, резкий ход - нет. */
    private static MarketCoefficient tolerant() {
        return new TolerantLevel(candles -> 100, new ChangeAngleSignalSource(BARS), 0.05, 0.5);
    }

    /** Правило с уже открытой позицией: сторона сменилась один раз, дальше спрашивается выход. */
    private static SignalSource opened(SignalSource ownExit, MarketCoefficient level) {
        double[] side = {1};
        SignalSource rule = TrendLevelRule.trendBranch(candles -> new Signal(side[0], 1), level, ownExit);
        List<Candle> warmUp = series(100, 0, 1);

        rule.calculate(warmUp);
        side[0] = -1;
        rule.calculate(warmUp);

        return rule;
    }

    private static SignalSource exit(SignalType type) {
        return candles -> new Signal(1, 1).withType(type);
    }

    /** Ход туда и обратно около уровня: угол сходится в ничью, решительности нет. */
    private static List<Candle> drifting() {
        return drifting(100);
    }

    private static List<Candle> drifting(double around) {
        List<Candle> candles = new ArrayList<>(BARS + 1);

        for (int at = 0; at <= BARS; at++) {
            candles.add(candle(at, around + (at % 2 == 0 ? 0 : 2), 1));
        }

        return candles;
    }

    private static List<Candle> series(double from, double step, long volume) {
        List<Candle> candles = new ArrayList<>(BARS + 1);

        for (int at = 0; at <= BARS; at++) {
            candles.add(candle(at, from + step * at, volume));
        }

        return candles;
    }

    private static Candle candle(int at, double price, long volume) {
        Quotation value = Quotation.of(price);

        return new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
            value, value, value, value, volume);
    }
}
