package com.siberalt.singularity.strategy.signal;

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
public record ThresholdSwitchSignalSource(
    SignalSource calculatorA,
    SignalSource calculatorB,
    double topThreshold,
    double bottomThreshold
) implements SignalSource {

    public static final double DEFAULT_TOP_THRESHOLD = 0.5;
    public static final double DEFAULT_BOTTOM_THRESHOLD = -0.5;

    public ThresholdSwitchSignalSource {
        if (calculatorA == null || calculatorB == null) {
            throw new IllegalArgumentException("CalculatorA and CalculatorB must not be null");
        }
    }

    public ThresholdSwitchSignalSource(SignalSource calculatorA, SignalSource calculatorB) {
        this(calculatorA, calculatorB, DEFAULT_TOP_THRESHOLD, DEFAULT_BOTTOM_THRESHOLD);
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        // Получаем сигнал от приоритетного калькулятора
        Signal signalA = calculatorA.calculate(lastCandles);

        // Если сигнал выходит за пороги, используем калькулятор A
        if (signalA.confidence() >= topThreshold || signalA.confidence() <= bottomThreshold) {
            return signalA;
        }

        // Иначе используем альтернативный калькулятор
        return calculatorB.calculate(lastCandles);
    }
}
