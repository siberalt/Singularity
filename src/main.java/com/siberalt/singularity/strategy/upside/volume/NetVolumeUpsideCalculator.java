package com.siberalt.singularity.strategy.upside.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;

/**
 * Калькулятор upside, основанный на соотношении объёма в "восходящих" и "нисходящих" свечах.
 *
 * <p>Свеча считается:</p>
 * <ul>
 *   <li><b>восходящей</b> — если закрытие >= открытия</li>
 *   <li><b>нисходящей</b> — если закрытие < открытия</li>
 * </ul>
 *
 * <p>Выходное значение ∈ [-1, +1]:</p>
 * <ul>
 *   <li><b>+1.0</b> — весь объём был в восходящих свечах (сильный бычий сигнал)</li>
 *   <li><b>-1.0</b> — весь объём в нисходящих (медвежий)</li>
 *   <li><b>0.0</b> — баланс между объёмами</li>
 * </ul>
 */
public class NetVolumeUpsideCalculator implements UpsideCalculator {
    public final static double DEFAULT_MIN_BODY_RATIO = 0.1;
    public final static double BODY_WEIGHT_FACTOR_DEFAULT_VALUE = 1.0;

    private final double minBodyRatio;
    private final double bodyWeightFactor;

    /**
     * Создаёт калькулятор с параметрами фильтрации и взвешивания.
     *
     * @param minBodyRatio     минимальное отношение (тело / диапазон) для учёта свечи (по умолчанию 0.1)
     * @param bodyWeightFactor множитель для веса на основе размера тела (по умолчанию 1.0)
     */
    public NetVolumeUpsideCalculator(double minBodyRatio, double bodyWeightFactor) {
        this.minBodyRatio = minBodyRatio;
        this.bodyWeightFactor = bodyWeightFactor;
    }

    public NetVolumeUpsideCalculator() {
        this(DEFAULT_MIN_BODY_RATIO, BODY_WEIGHT_FACTOR_DEFAULT_VALUE);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        double upVolume = 0.0;
        double downVolume = 0.0;

        for (Candle candle : lastCandles) {
            double volume = candle.volume();

            if (volume <= 0) {
                continue; // Пропускаем свечи с нулевым объёмом
            }

            double high = candle.getHighAsDouble();
            double low = candle.getLowAsDouble();
            double open = candle.getOpenAsDouble();
            double close = candle.getCloseAsDouble();

            double range = high - low;
            if (range == 0) continue;

            double body = Math.abs(close - open);
            double bodyRatio = body / range;

            // Фильтрация шума: игнорируем свечи с очень малым телом
            if (bodyRatio < minBodyRatio) {
                continue;
            }

            // Взвешивание по силе движения
            double weight = 1.0 + bodyWeightFactor * (bodyRatio - minBodyRatio) / (1.0 - minBodyRatio);

            if (close >= open) {
                upVolume += volume * weight;
            } else {
                downVolume += volume * weight;
            }
        }

        double totalVolume = upVolume + downVolume;

        // Защита от деления на ноль
        if (totalVolume == 0) {
            return Upside.NEUTRAL;
        }

        // Рассчитываем чистый объёмный импульс: [-1, +1]
        double netVolumeRatio = (upVolume - downVolume) / totalVolume;

        return new Upside(netVolumeRatio, Math.abs(netVolumeRatio));
    }
}
