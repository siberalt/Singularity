package com.siberalt.singularity.strategy.upside.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.upside.Upside;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NetVolumeUpsideCalculatorTest {

    private NetVolumeUpsideCalculator calculator;
    private List<Candle> candles;

    @BeforeEach
    void setUp() {
        calculator = new NetVolumeUpsideCalculator();
        candles = new ArrayList<>();
    }

    private Candle createCandle(double open, double close, long volume) {
        return Candle.of(
            Instant.now().plusSeconds(candles.size() * 60L),
            volume,
            open,
            Math.max(open, close) + 0.1,
            Math.min(open, close) - 0.1,
            close
        );
    }

    @Test
    @DisplayName("Должен вернуть 0 при пустом списке")
    void shouldReturnZeroForEmptyList() {
        Upside result = calculator.calculate(List.of());
        assertEquals(0.0, result.signal(), 0.001);
    }

    @Test
    @DisplayName("Должен вернуть 0 при null-списке")
    void shouldReturnZeroForNullList() {
        Upside result = calculator.calculate(null);
        assertEquals(0.0, result.signal(), 0.001);
    }

    @Test
    @DisplayName("Должен вернуть +1.0 при всех восходящих свечах")
    void shouldReturnMaxPositiveWhenAllBullish() {
        candles.add(createCandle(100, 102, 1000));
        candles.add(createCandle(102, 105, 1500));
        candles.add(createCandle(105, 108, 2000));

        Upside result = calculator.calculate(candles);

        assertEquals(+1.0, result.signal(), 0.001);
    }

    @Test
    @DisplayName("Должен вернуть -1.0 при всех нисходящих свечах")
    void shouldReturnMaxNegativeWhenAllBearish() {
        candles.add(createCandle(100, 98, 1000));
        candles.add(createCandle(98, 95, 1500));
        candles.add(createCandle(95, 92, 2000));

        Upside result = calculator.calculate(candles);

        assertEquals(-1.0, result.signal(), 0.001);
    }

    @Test
    @DisplayName("Должен вернуть 0 при балансе объёмов")
    void shouldReturnZeroWhenVolumesBalanced() {
        candles.add(createCandle(100, 102, 1000)); // +1000
        candles.add(createCandle(102, 100, 1000)); // -1000

        Upside result = calculator.calculate(candles);

        assertEquals(0.0, result.signal(), 0.001);
    }

    @Test
    @DisplayName("Должен корректно рассчитать соотношение при смешанных свечах")
    void shouldCalculateCorrectRatioForMixedCandles() {
        candles.add(createCandle(100, 102, 1500)); // +1500
        candles.add(createCandle(102, 100, 500));  // -500
        candles.add(createCandle(100, 103, 1000)); // +1000

        // upVol = 2500, downVol = 500 → ratio = (2500-500)/(3000) = 2000/3000 ≈ 0.666
        Upside result = calculator.calculate(candles);

        assertEquals(2000.0 / 3000.0, result.signal(), 0.002);
    }

    @Test
    @DisplayName("Должен игнорировать свечи с нулевым объёмом")
    void shouldIgnoreZeroVolumeCandles() {
        candles.add(createCandle(100, 102, 1000));
        candles.add(createCandle(102, 100, 0));
        candles.add(createCandle(100, 101, 1000));

        // +1000 и +1000 → все вверх → +1.0
        Upside result = calculator.calculate(candles);

        assertEquals(+1.0, result.signal(), 0.0015);
    }

    @Test
    @DisplayName("Должен вернуть 0 при всех нулевых объёмах")
    void shouldReturnZeroWhenAllVolumesAreZero() {
        candles.add(createCandle(100, 102, 0));
        candles.add(createCandle(102, 100, 0));

        Upside result = calculator.calculate(candles);

        assertEquals(0.0, result.signal(), 0.001);
    }

    @Test
    @DisplayName("Должен корректно обрабатывать одну свечу")
    void shouldHandleSingleCandle() {
        candles.add(createCandle(100, 101, 500)); // up

        Upside result = calculator.calculate(candles);

        assertEquals(+1.0, result.signal(), 0.001);
    }
}
