package com.siberalt.singularity.strategy.upside.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.upside.InvertedUpsideCalculator;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;

/**
 * Классический бинарный калькулятор на основе объёма.
 * Сигнал = (upVolume - downVolume) / (upVolume + downVolume),
 * где upVolume – объём свечей с close >= open,
 * downVolume – объём свечей с close < open.
 * <p>Для устранения ложных сигналов на экстремумах используйте декоратор {@link InvertedUpsideCalculator}.
 */
public record BinaryNetVolumeUpsideCalculator(
    double minBodyRatio,
    double bodyWeightFactor,
    double maxNoiseRatio,
    boolean ignoreLowBody
) implements UpsideCalculator {

    public static final double DEFAULT_MIN_BODY_RATIO = 0.08;
    public static final double DEFAULT_BODY_WEIGHT_FACTOR = 0.4;
    public static final double DEFAULT_MAX_NOISE_RATIO = 0.7;
    public static final boolean DEFAULT_IGNORE_LOW_BODY = false;

    public BinaryNetVolumeUpsideCalculator {
        if (minBodyRatio < 0) minBodyRatio = 0;
        if (bodyWeightFactor < 0) bodyWeightFactor = 0;
        if (maxNoiseRatio < 0) maxNoiseRatio = 0.7;
    }

    public BinaryNetVolumeUpsideCalculator(double minBodyRatio, double bodyWeightFactor, double maxNoiseRatio) {
        this(minBodyRatio, bodyWeightFactor, maxNoiseRatio, DEFAULT_IGNORE_LOW_BODY);
    }

    public BinaryNetVolumeUpsideCalculator() {
        this(DEFAULT_MIN_BODY_RATIO, DEFAULT_BODY_WEIGHT_FACTOR, DEFAULT_MAX_NOISE_RATIO);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        double upVolume = 0.0;
        double downVolume = 0.0;
        int usedCandles = 0;

        for (Candle candle : lastCandles) {
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
                bodyRatio = body / range;
            }

            double weight;
            if (bodyRatio >= minBodyRatio) {
                double t = (bodyRatio - minBodyRatio) / (1.0 - minBodyRatio);
                weight = 1.0 + bodyWeightFactor * t;
            } else if (ignoreLowBody) {
                continue;
            } else {
                weight = (bodyRatio / minBodyRatio);

                if (weight <= 0 ) continue;
            }

            if (close >= open) {
                upVolume += volume * weight;
            } else {
                downVolume += volume * weight;
            }
            usedCandles++;
        }

        if (usedCandles == 0) {
            return Upside.NEUTRAL;
        }

        double totalVolume = upVolume + downVolume;
        if (totalVolume == 0) {
            return Upside.NEUTRAL;
        }

        double signal = (upVolume - downVolume) / totalVolume;
        double strength = Math.min(1.0, (double) usedCandles / lastCandles.size());

        double noise = 1.0 - strength;
        if (noise > maxNoiseRatio) {
            double factor = (1.0 - noise) / (1.0 - maxNoiseRatio);
            signal *= factor;
            strength *= factor;
        }

        return new Upside(signal, strength);
    }
}
