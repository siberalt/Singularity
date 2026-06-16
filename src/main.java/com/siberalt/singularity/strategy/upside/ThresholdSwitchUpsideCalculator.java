package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Переключатель между двумя калькуляторами апсайда на основе порогов.
 * <p>
 * Использует calculatorA в качестве приоритетного калькулятора.
 * Если его сигнал выходит за заданные пороги (topThreshold или bottomThreshold),
 * возвращается результат от калькулятора A. Иначе возвращается результат от
 * калькулятора B.
 * </p>
 *
 * @param calculatorA     Приоритетный калькулятор (используется при выходе за пороги)
 * @param calculatorB     Альтернативный калькулятор (используется внутри порогов)
 * @param topThreshold    Верхний порог сигнала для переключения (например 0.5)
 * @param bottomThreshold Нижний порог сигнала для переключения (например -0.5)
 */
public record ThresholdSwitchUpsideCalculator(
    UpsideCalculator calculatorA,
    UpsideCalculator calculatorB,
    double topThreshold,
    double bottomThreshold
) implements UpsideCalculator {

    public static final double DEFAULT_TOP_THRESHOLD = 0.5;
    public static final double DEFAULT_BOTTOM_THRESHOLD = -0.5;

    public ThresholdSwitchUpsideCalculator {
        if (calculatorA == null || calculatorB == null) {
            throw new IllegalArgumentException("CalculatorA and CalculatorB must not be null");
        }
    }

    public ThresholdSwitchUpsideCalculator(UpsideCalculator calculatorA, UpsideCalculator calculatorB) {
        this(calculatorA, calculatorB, DEFAULT_TOP_THRESHOLD, DEFAULT_BOTTOM_THRESHOLD);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        // Получаем сигнал от приоритетного калькулятора
        Upside signalA = calculatorA.calculate(lastCandles);

        // Если сигнал выходит за пороги, используем калькулятор A
        if (signalA.signal() >= topThreshold || signalA.signal() <= bottomThreshold) {
            return signalA;
        }

        // Иначе используем альтернативный калькулятор
        return calculatorB.calculate(lastCandles);
    }
}
