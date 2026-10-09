package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Декоратор SignalSource, который усиливает сигнал делегата до экстремальных значений.
 * <p>
 * Если сигнал делегата превышает заданные пороги, возвращает экстремальное значение (+1 или -1).
 * В противном случае возвращает сигнал делегата без изменений.
 * </p>
 * <p>
 * Возвращает:
 * <ul>
 *   <li><b>+1.0</b> — если сигнал от делегата {@code s > positiveThreshold}</li>
 *   <li><b>-1.0</b> — если сигнал от делегата {@code s < -negativeThreshold}</li>
 *   <li><b>signal</b> — сигнал делегата в остальных случаях (нейтральная зона)</li>
 * </ul>
 * </p>
 */
public record SignalAmplifier(SignalSource delegate,
                                    double positiveThreshold,
                                    double negativeThreshold) implements SignalSource {

    /**
     * Конструктор с разными порогами для положительной и отрицательной зон.
     *
     * @param delegate          Декорируемый калькулятор
     * @param positiveThreshold Порог положительной зоны (должен быть в диапазоне [0, 1])
     * @param negativeThreshold Порог отрицательной зоны (должен быть в диапазоне [0, 1])
     */
    public SignalAmplifier {
        if (delegate == null) {
            throw new IllegalArgumentException("Delegate cannot be null");
        }
        if (positiveThreshold < 0 || positiveThreshold > 1) {
            throw new IllegalArgumentException("Positive threshold must be in range [0, 1]");
        }
        if (negativeThreshold < 0 || negativeThreshold > 1) {
            throw new IllegalArgumentException("Negative threshold must be in range [0, 1]");
        }
    }

    /**
     * Конструктор с одинаковыми порогами для обеих зон.
     *
     * @param delegate  Декорируемый калькулятор
     * @param threshold Порог для обеих зон (должен быть в диапазоне [0, 1])
     */
    public SignalAmplifier(SignalSource delegate, double threshold) {
        this(delegate, threshold, threshold);
    }

    /**
     * Конструктор с порогом по умолчанию 0.5 для обеих зон.
     *
     * @param delegate Декорируемый калькулятор
     */
    public SignalAmplifier(SignalSource delegate) {
        this(delegate, 0.5, 0.5);
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        Signal delegateSignal = delegate.calculate(lastCandles);
        double signal = delegateSignal.confidence();

        if (signal > positiveThreshold) {
            return new Signal(1.0, signal);
        } else if (signal < -negativeThreshold) {
            return new Signal(-1.0, signal);
        } else {
            return delegateSignal;
        }
    }
}
