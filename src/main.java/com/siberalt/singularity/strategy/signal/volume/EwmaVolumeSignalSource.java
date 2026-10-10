package com.siberalt.singularity.strategy.signal.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;

import java.util.List;

/**
 * Объёмный калькулятор с экспоненциально убывающими весами свечей.
 * <p>Новые свечи имеют больший вес, старые забываются экспоненциально.
 * Это решает проблему «залипания» сигнала после кульминационного объёма.</p>
 *
 * @param minBodyRatio      минимальное тело/диапазон (0..1)
 * @param bodyWeightFactor  усиление веса для больших тел (0..∞)
 * @param maxNoiseRatio     max доля отфильтрованных свечей (0..1)
 * @param ignoreLowBody     игнорировать свечи с bodyRatio < minBodyRatio
 * @param halfLife          период полураспада (количество свечей, через которое вес падает в 2 раза)
 */
public record EwmaVolumeSignalSource(
    double minBodyRatio,
    double bodyWeightFactor,
    double maxNoiseRatio,
    boolean ignoreLowBody,
    double halfLife
) implements SignalSource {

    public static final double DEFAULT_MIN_BODY_RATIO = 0.08;
    public static final double DEFAULT_BODY_WEIGHT_FACTOR = 0.4;
    public static final double DEFAULT_MAX_NOISE_RATIO = 0.7;
    public static final boolean DEFAULT_IGNORE_LOW_BODY = false;
    public static final double DEFAULT_HALF_LIFE = 12.0;   // через 12 свечей вес падает вдвое

    public EwmaVolumeSignalSource {
        if (halfLife <= 0) halfLife = 1;
    }

    public EwmaVolumeSignalSource(double minBodyRatio, double bodyWeightFactor, double maxNoiseRatio, boolean ignoreLowBody) {
        this(minBodyRatio, bodyWeightFactor, maxNoiseRatio, ignoreLowBody, DEFAULT_HALF_LIFE);
    }

    public EwmaVolumeSignalSource(double minBodyRatio, double bodyWeightFactor, double maxNoiseRatio) {
        this(minBodyRatio, bodyWeightFactor, maxNoiseRatio, DEFAULT_IGNORE_LOW_BODY);
    }

    public EwmaVolumeSignalSource() {
        this(DEFAULT_MIN_BODY_RATIO, DEFAULT_BODY_WEIGHT_FACTOR, DEFAULT_MAX_NOISE_RATIO);
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Signal.NEUTRAL;
        }

        // Вычисляем alpha по формуле EMA: alpha = 2/(period+1), но для полураспада:
        // (1 - alpha)^halfLife = 0.5  =>  alpha = 1 - 0.5^(1/halfLife)
        double alpha = 1.0 - Math.pow(0.5, 1.0 / halfLife);
        // Для численной стабильности
        alpha = Math.min(0.5, Math.max(0.01, alpha));

        double weightedSignedSum = 0.0;
        double totalWeightedVolume = 0.0;
        int usedCandles = 0;
        int n = lastCandles.size();

        for (int idx = 0; idx < n; idx++) {
            Candle candle = lastCandles.get(idx);
            double volume = candle.volume();
            if (volume <= 0) continue;

            double high = candle.getHighAsDouble();
            double low = candle.getLowAsDouble();
            double open = candle.getOpenAsDouble();
            double close = candle.getCloseAsDouble();

            double range = high - low;
            double bodyRatio;
            if (range == 0) {
                bodyRatio = 1.0;
            } else {
                double body = Math.abs(close - open);
                // Обрезается, потому что тело больше диапазона у свечи, которая этого не может: у
                // корректной high не ниже открытия с закрытием, а low не выше. Такая свеча раздувала вес
                // выше объявленного максимума 1 + bodyWeightFactor, а дальше вылезал и сам сигнал.
                bodyRatio = Math.min(1.0, body / range);
            }

            double weight;
            if (bodyRatio >= minBodyRatio) {
                double t = (bodyRatio - minBodyRatio) / (1.0 - minBodyRatio);
                weight = 1.0 + bodyWeightFactor * t;
            } else if (ignoreLowBody) {
                continue;
            } else {
                weight = 0.2 + 0.8 * (bodyRatio / minBodyRatio);
                if (weight <= 0) continue;
            }

            // Та же обрезка и по той же причине: у свечи с телом больше диапазона направление выходит за
            // единицу, а больше чем «целиком в одну сторону» оно означать не может. Обрезается здесь, а
            // не на итоговом сигнале: иначе одна битая свеча продолжала бы перетягивать среднее, просто
            // уже внутри допустимых границ.
            double direction = range == 0 ? 0 : Math.clamp((close - open) / range, -1.0, 1.0);
            double contribution = direction * volume * weight;

            // Экспоненциальный вес для этой свечи: (1 - alpha)^(n-1-idx) * alpha?
            // В классическом EMA последняя свеча имеет вес alpha, предыдущая alpha*(1-alpha) и т.д.
            // Но для нормировки удобнее использовать: ewma = сумма (value_i * (1-alpha)^(n-1-i)) / сумма ((1-alpha)^(n-1-i))
            // без дополнительного alpha в числителе и знаменателе.
            double expWeight = Math.pow(1.0 - alpha, n - 1 - idx);
            // Можно также добавить alpha, но тогда знаменатель нормируется иначе.
            // Используем простой вариант: вес экспоненциально убывает от 1 (последняя свеча) к 0.
            // Тогда числитель и знаменатель — взвешенные суммы.
            weightedSignedSum += contribution * expWeight;
            totalWeightedVolume += volume * weight * expWeight;
            usedCandles++;
        }

        if (usedCandles == 0 || totalWeightedVolume == 0) {
            return Signal.NEUTRAL;
        }

        // Взвешенное среднее направлений с положительными весами, поэтому в [-1, 1] - но только потому,
        // что каждое направление обрезано выше. До этой обрезки здесь выходило до +-2.03, и поскольку
        // SignalScaledQuantity умножает на уверенность лоты, это была заявка на двести процентов счёта.
        double signal = weightedSignedSum / totalWeightedVolume;
        double strength = Math.min(1.0, (double) usedCandles / lastCandles.size());

        // Плавное затухание при высоком шуме
        double noise = 1.0 - strength;
        if (noise > maxNoiseRatio) {
            double factor = (1.0 - noise) / (1.0 - maxNoiseRatio);
            signal *= factor;
            strength *= factor;
        }

        return new Signal(signal, strength);
    }
}
