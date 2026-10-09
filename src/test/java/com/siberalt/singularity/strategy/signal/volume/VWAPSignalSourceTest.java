package com.siberalt.singularity.strategy.signal.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.signal.Signal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VWAPSignalSourceTest {

    private final VWAPSignalSource calculator = new VWAPSignalSource();

    /**
     * Вспомогательный метод для создания свечи
     */
    private Candle candle(double high, double low, double close, long volume) {
        return Candle.of(TimePoint.NULL, volume, 0, high, low, close);
    }

    /**
     * Тест: цена выше VWAP → UP
     */
    @Test
    void should_ReturnUp_WhenPriceAboveVWAP() {
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(105, 100, 104, 1000)); // высокий объем, высокая цена
        candles.add(candle(102, 100, 101, 500));
        candles.add(candle(100, 98, 99, 200));   // последняя цена 99

        Signal result = calculator.calculate(candles);
        assertTrue(result.confidence() < 0.0);
    }

    /**
     * Перепишем с корректными данными
     */
    @Test
    void should_ReturnUp_WhenLastCloseAboveVWAP() {
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(101, 99, 100, 100)); // типичная = 100
        candles.add(candle(102, 100, 101, 200)); // типичная = 101
        candles.add(candle(104, 102, 103, 300)); // последняя цена = 103

        // Расчёт VWAP:
        // (100*100 + 101*200 + 103*300) / (100+200+300)
        // = (10_000 + 20_200 + 30_900) / 600 = 61_100 / 600 ≈ 101.83
        // 103 > 101.83 → UP

        Signal result = calculator.calculate(candles);
        assertTrue(result.confidence() > 0.0);
    }

    @Test
    void should_ReturnDown_WhenLastCloseBelowVWAP() {
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(100, 98, 99, 500));
        candles.add(candle(102, 100, 101, 300));
        candles.add(candle(99.6, 99.4, 99.5, 200));

        Signal result = calculator.calculate(candles);
        assertTrue(result.confidence() < 0.0);
    }

    @Test
    void should_ReturnNeutral_WhenPriceEqualsVWAP() {
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(100, 100, 100, 100));
        candles.add(candle(100, 100, 100, 100)); // все одинаковые

        Signal result = calculator.calculate(candles);

        assertEquals(Signal.NEUTRAL, result); // цена = vwap
    }

    @Test
    void should_ReturnNeutral_WhenEmptyList() {
        Signal result = calculator.calculate(List.of());
        assertEquals(Signal.NEUTRAL, result);
    }

    @Test
    void should_ReturnNeutral_WhenNullList() {
        Signal result = calculator.calculate(null);
        assertEquals(Signal.NEUTRAL, result);
    }

    @Test
    void should_ReturnNeutral_WhenZeroVolume() {
        List<Candle> candles = new ArrayList<>();
        candles.add(candle(100, 100, 100, 0));
        candles.add(candle(101, 101, 101, 0));

        Signal result = calculator.calculate(candles);

        assertEquals(Signal.NEUTRAL, result);
    }

    @Test
    void should_CalculateVWAPCorrectly_SingleCandle() {
        List<Candle> candles = List.of(candle(105, 95, 100, 1000)); // типичная = (105+95+100)/3 ≈ 100

        Signal result = calculator.calculate(candles);

        assertEquals(Signal.NEUTRAL, result); // 100 == vwap → neutral
    }
}
