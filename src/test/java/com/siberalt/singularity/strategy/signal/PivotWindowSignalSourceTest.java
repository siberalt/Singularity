package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.extreme.ZigZagExtremeLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Декоратор, показывающий делегату только текущую ногу. */
class PivotWindowSignalSourceTest {
    private static final double THRESHOLD = 1;

    /** Делегату достаётся нога, и ровно она: от разворота до последнего бара. */
    @Test
    void showsTheDelegateTheLegAndNothingElse() {
        List<Candle> candles = legs(15, 3, 15, -3);
        List<List<Candle>> seen = new ArrayList<>();
        double peak = candles.stream().mapToDouble(Candle::getCloseAsDouble).max().orElseThrow();

        anchored(window -> {
            seen.add(window);

            return Signal.NEUTRAL;
        }).calculate(candles);

        assertEquals(1, seen.size(), "делегата спросили один раз");
        assertEquals(peak, seen.getFirst().getFirst().getCloseAsDouble(), 1e-9,
            "нога начинается на развороте");
        assertEquals(candles.getLast(), seen.getFirst().getLast(), "и кончается последним баром");
    }

    /** Ответ делегата проходит насквозь: декоратор ничего не меряет сам. */
    @Test
    void passesTheDelegateAnswerThrough() {
        Signal own = new Signal(0.5, 0.25);

        assertEquals(own, anchored(window -> own).calculate(legs(15, 3, 15, -3)));
    }

    /**
     * Тот же угол на том же ряду читается иначе, когда окно поставлено на разворот. Ради этого
     * декоратор и нужен: у нарезки по счёту баров граница может сесть поперёк разворота.
     */
    @Test
    void readsDifferentlyThanAFixedWindow() {
        List<Candle> candles = legs(12, 3, 8, -3);
        ChangeAngleSignalSource angle = new ChangeAngleSignalSource(15);

        assertTrue(angle.calculate(candles).confidence() > -0.9,
            "окно в 15 баров сидит поперёк разворота и видит ход туда-обратно");
        assertEquals(-1, anchored(new ChangeAngleSignalSource(5)).calculate(candles).confidence(),
            1e-9, "нога после разворота идёт только вниз");
    }

    /** Пока разворота нет, делегата не спрашивают вовсе. */
    @Test
    void saysNothingWithoutAPivot() {
        List<Candle> straight = legs(40, 1);

        assertEquals(Signal.NEUTRAL, anchored(window -> new Signal(1, 1)).calculate(straight));
        assertEquals(Signal.NEUTRAL, anchored(window -> new Signal(1, 1)).calculate(null));
        assertEquals(Signal.NEUTRAL, anchored(window -> new Signal(1, 1)).calculate(legs(2, 1)));
    }

    /**
     * Нога короче заказанного закрывает источник, а не подменяется фиксированным окном: вернуть
     * делегату другие бары значило бы молча ответить не на тот вопрос.
     */
    @Test
    void saysNothingWhileTheLegIsTooShort() {
        List<Candle> candles = legs(15, 3, 15, -3);

        assertEquals(Signal.NEUTRAL, new PivotWindowSignalSource(
            window -> new Signal(1, 1), new ZigZagExtremeLocator(THRESHOLD), 20).calculate(candles));
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        ZigZagExtremeLocator locator = new ZigZagExtremeLocator(THRESHOLD);

        assertThrows(IllegalArgumentException.class,
            () -> new PivotWindowSignalSource(null, locator));
        assertThrows(IllegalArgumentException.class,
            () -> new PivotWindowSignalSource(window -> Signal.NEUTRAL, (ZigZagExtremeLocator) null));
        assertThrows(IllegalArgumentException.class,
            () -> new PivotWindowSignalSource(window -> Signal.NEUTRAL, locator, 2));
    }

    private static PivotWindowSignalSource anchored(SignalSource delegate) {
        return new PivotWindowSignalSource(delegate, new ZigZagExtremeLocator(THRESHOLD));
    }

    private static List<Candle> legs(double... lengthsAndSlopes) {
        List<Candle> candles = new ArrayList<>();
        double price = 1000;

        for (int leg = 0; leg < lengthsAndSlopes.length; leg += 2) {
            for (int at = 0; at < (int) lengthsAndSlopes[leg]; at++) {
                Quotation value = Quotation.of(price);

                candles.add(new Candle(1,
                    new TimePoint(candles.size(), Instant.EPOCH.plusSeconds(60L * candles.size())),
                    value, value, value, value, 1));
                price += lengthsAndSlopes[leg + 1];
            }
        }

        return candles;
    }
}
