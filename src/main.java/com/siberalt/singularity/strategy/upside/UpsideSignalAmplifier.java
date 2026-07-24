package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Декоратор UpsideCalculator, который усиливает сигнал делегата до экстремальных значений.
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
public record UpsideSignalAmplifier(UpsideCalculator delegate,
                                    double positiveThreshold,
                                    double negativeThreshold) implements UpsideCalculator {

    /**
     * Конструктор с разными порогами для положительной и отрицательной зон.
     *
     * @param delegate          Декорируемый калькулятор
     * @param positiveThreshold Порог положительной зоны (должен быть в диапазоне [0, 1])
     * @param negativeThreshold Порог отрицательной зоны (должен быть в диапазоне [0, 1])
     */
    public UpsideSignalAmplifier {
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
    public UpsideSignalAmplifier(UpsideCalculator delegate, double threshold) {
        this(delegate, threshold, threshold);
    }

    /**
     * Конструктор с порогом по умолчанию 0.5 для обеих зон.
     *
     * @param delegate Декорируемый калькулятор
     */
    public UpsideSignalAmplifier(UpsideCalculator delegate) {
        this(delegate, 0.5, 0.5);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        Upside delegateUpside = delegate.calculate(lastCandles);
        double signal = delegateUpside.signal();

        if (signal > positiveThreshold) {
            return new Upside(1.0, signal);
        } else if (signal < -negativeThreshold) {
            return new Upside(-1.0, signal);
        } else {
            return delegateUpside;
        }
    }
}
