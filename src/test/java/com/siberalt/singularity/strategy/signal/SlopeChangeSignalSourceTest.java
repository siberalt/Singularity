package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Кривизна хода: наклон позднего отрезка против раннего. */
class SlopeChangeSignalSourceTest {
    private static final int HALF = 10;

    /**
     * Ровный тренд даёт ноль, а не единицу. Это главное свойство меры и первое, обо что об неё
     * спотыкаются: ноль здесь значит «наклон не менялся», а не «тренда нет».
     */
    @Test
    void readsASteadyTrendAsNoChangeAtAll() {
        assertEquals(0, signalOf(slopes(2, 2)).confidence(), 1e-12);
        assertEquals(0, signalOf(slopes(-2, -2)).confidence(), 1e-12);
    }

    @Test
    void readsAnAcceleratingRiseAsPositive() {
        assertEquals(0.5, signalOf(slopes(1, 3)).confidence(), 1e-12);
    }

    @Test
    void readsAFadingRiseAsNegative() {
        assertEquals(-0.5, signalOf(slopes(3, 1)).confidence(), 1e-12);
    }

    /**
     * Замедляющееся падение - это поворот вверх, и знак обязан быть плюсом. У напрашивающейся формулы
     * {@code (sl − sw) / sw} здесь выходил минус: деление на отрицательное переворачивает знак.
     */
    @Test
    void readsAFadingFallAsTurningUp() {
        assertEquals(0.5, signalOf(slopes(-3, -1)).confidence(), 1e-12);
    }

    /**
     * И зеркально: ускоряющийся обвал - это минус. У формулы с делением на {@code sw} здесь выходил
     * плюс, то есть сигнал на покупку посреди ускоряющегося падения.
     */
    @Test
    void readsAnAcceleratingFallAsNegative() {
        assertEquals(-0.5, signalOf(slopes(-1, -3)).confidence(), 1e-12);
    }

    /** Разворот внутри окна - предел шкалы: отрезки смотрят в разные стороны. */
    @Test
    void readsAReversalInsideTheWindowAsTheLimit() {
        assertEquals(1, signalOf(slopes(-2, 2)).confidence(), 1e-12);
        assertEquals(-1, signalOf(slopes(2, -2)).confidence(), 1e-12);
    }

    /** Неподвижному ходу ускоряться нечем, и ноль для него - ответ, а не молчание. */
    @Test
    void readsAMotionlessWindowAsZero() {
        Signal signal = signalOf(slopes(0, 0));

        assertEquals(0, signal.confidence(), 1e-12);
        assertEquals(SignalType.UNSPECIFIED, signal.type(), "это ответ, а не NEUTRAL");
    }

    /**
     * Мера однородна нулевой степени: домножьте оба наклона на что угодно положительное, и ответ тот
     * же. Поэтому бумага за 100 и бумага за 10000 сравнимы без деления на цену.
     */
    @Test
    void answersTheSameWhateverThePriceLevel() {
        assertEquals(signalOf(slopes(1, 3)).confidence(),
            signalOf(scaled(slopes(1, 3), 100)).confidence(), 1e-12);
    }

    /** Сила - наименьшее из двух R²: дёрганый отрезок делает весь ответ ненадёжным. */
    @Test
    void putsTheWorseFitIntoStrength() {
        assertEquals(1, signalOf(slopes(1, 3)).strength(), 1e-9, "оба отрезка - прямые");

        List<Candle> noisy = slopes(1, 3);

        // Дёргаем ранний отрезок, не трогая его общий наклон по концам.
        for (int at = 1; at < HALF; at += 2) {
            noisy.set(at, candle(at, noisy.get(at).getCloseAsDouble() + 20));
        }

        assertTrue(signalOf(noisy).strength() < 0.5,
            "дёрганый отрезок - низкая сила, получено " + signalOf(noisy).strength());
    }

    /** Пока обоих отрезков не набралось, сказать нечего. */
    @Test
    void saysNothingUntilBothStretchesAreThere() {
        SlopeChangeSignalSource source = new SlopeChangeSignalSource(HALF);
        List<Candle> window = slopes(1, 3);

        assertEquals(2 * HALF, source.requiredCandles());
        assertEquals(Signal.NEUTRAL, source.calculate(window.subList(0, 2 * HALF - 1)));
        assertEquals(Signal.NEUTRAL, source.calculate(null));
    }

    /** Берутся последние два отрезка, а не первые: мера про то, что происходит сейчас. */
    @Test
    void readsTheLastTwoStretchesAndIgnoresWhatCameBefore() {
        List<Candle> longer = new ArrayList<>(slopes(5, -5));

        longer.addAll(scaled(slopes(1, 3), 1));

        assertEquals(0.5, signalOf(longer).confidence(), 1e-12);
    }

    /**
     * Отрезки разной длины: короткий щуп против длинной базы. Число то же, что у равных отрезков с
     * теми же наклонами, - мера смотрит на наклоны, а не на то, по скольким барам они проведены.
     */
    @Test
    void comparesStretchesOfDifferentLength() {
        SlopeChangeSignalSource uneven = new SlopeChangeSignalSource(20, 5);

        assertEquals(25, uneven.requiredCandles());
        assertEquals(0.5, uneven.calculate(slopes(20, 1, 5, 3)).confidence(), 1e-12);
        assertEquals(-0.5, uneven.calculate(slopes(20, 3, 5, 1)).confidence(), 1e-12);
    }

    /** Короткий отрезок берётся с конца, длинный - перед ним, а не наоборот. */
    @Test
    void takesTheShortStretchFromTheEnd() {
        SlopeChangeSignalSource uneven = new SlopeChangeSignalSource(20, 5);
        List<Candle> window = slopes(20, 1, 5, -1);

        assertEquals(-1, uneven.calculate(window).confidence(), 1e-12,
            "последние пять баров идут вниз, значит ход развернулся");
    }

    /** Конструктор с одним числом - это равные отрезки, и ничего больше. */
    @Test
    void takesOneNumberAsTwoEqualStretches() {
        assertEquals(new SlopeChangeSignalSource(HALF, HALF).requiredCandles(),
            new SlopeChangeSignalSource(HALF).requiredCandles());
    }

    @Test
    void refusesWhatCannotBeAssembled() {
        assertThrows(IllegalArgumentException.class, () -> new SlopeChangeSignalSource(2));
        assertThrows(IllegalArgumentException.class, () -> new SlopeChangeSignalSource(2, HALF));
        assertThrows(IllegalArgumentException.class, () -> new SlopeChangeSignalSource(HALF, 2));
        assertThrows(IllegalArgumentException.class,
            () -> new SlopeChangeSignalSource(HALF).setPriceExtractor(null));
    }

    private static Signal signalOf(List<Candle> candles) {
        return new SlopeChangeSignalSource(HALF).calculate(candles);
    }

    /** Окно из двух отрезков с заданными наклонами, сшитых без разрыва. */
    private static List<Candle> slopes(double earlier, double later) {
        return slopes(HALF, earlier, HALF, later);
    }

    private static List<Candle> slopes(int earlierBars, double earlier, int laterBars, double later) {
        List<Candle> candles = new ArrayList<>(earlierBars + laterBars);
        double price = 1000;

        for (int at = 0; at < earlierBars; at++) {
            candles.add(candle(at, price));
            price += earlier;
        }

        for (int at = 0; at < laterBars; at++) {
            candles.add(candle(earlierBars + at, price));
            price += later;
        }

        return candles;
    }

    private static List<Candle> scaled(List<Candle> candles, double factor) {
        List<Candle> scaled = new ArrayList<>(candles.size());

        for (int at = 0; at < candles.size(); at++) {
            scaled.add(candle(at, candles.get(at).getCloseAsDouble() * factor));
        }

        return scaled;
    }

    private static Candle candle(int at, double price) {
        Quotation value = Quotation.of(price);

        return new Candle(1, new TimePoint(at, Instant.EPOCH.plusSeconds(60L * at)),
            value, value, value, value, 1);
    }
}
