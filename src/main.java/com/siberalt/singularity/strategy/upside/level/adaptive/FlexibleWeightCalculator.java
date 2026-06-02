package com.siberalt.singularity.strategy.upside.level.adaptive;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.level.proximity.DefaultPriceProximityFunction;
import com.siberalt.singularity.strategy.upside.level.proximity.PriceProximityFunction;

import java.util.List;

/**
 * Плавный адаптивный калькулятор весов для комбинирования апсайдов уровней и объёмов.
 * <p>
 * Формула для веса объёмов:
 * <pre>
 * volumeWeight = clamp( baseVolumeWeight
 *                     + absVolumeSignalWeight * |volume.signal()|
 *                     + levelProximityBonus * isNearLevel
 *                     + divergenceBonus * isDivergent,
 *                     minWeight, maxWeight )
 * levelsWeight = 1 - volumeWeight (затем нормализация)
 * </pre>
 */
public class FlexibleWeightCalculator implements WeightCalculator {

    private final double baseVolumeWeight;      // базовый вес объёмов (0.4)
    private final double absVolumeSignalWeight; // коэф. влияния абсолютной силы объёмного сигнала
    private final double levelProximityBonus;   // бонус к весу объёмов при близости к уровню
    private final double divergenceBonus;       // бонус к весу объёмов при дивергенции
    private final double minWeight;             // минимальный вес для любого компонента (0.2)
    private final double maxWeight;             // максимальный вес (0.8)

    private final PriceProximityFunction proximityFunction;

    /**
     * Конструктор со всеми параметрами.
     */
    public FlexibleWeightCalculator(double baseVolumeWeight,
                                    double absVolumeSignalWeight,
                                    double levelProximityBonus,
                                    double divergenceBonus,
                                    double minWeight,
                                    double maxWeight,
                                    PriceProximityFunction proximityFunction) {
        this.baseVolumeWeight = baseVolumeWeight;
        this.absVolumeSignalWeight = absVolumeSignalWeight;
        this.levelProximityBonus = levelProximityBonus;
        this.divergenceBonus = divergenceBonus;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
        this.proximityFunction = proximityFunction;
    }

    public FlexibleWeightCalculator(double baseVolumeWeight,
                                    double absVolumeSignalWeight,
                                    double levelProximityBonus,
                                    double divergenceBonus,
                                    double minWeight,
                                    double maxWeight) {
        this.baseVolumeWeight = baseVolumeWeight;
        this.absVolumeSignalWeight = absVolumeSignalWeight;
        this.levelProximityBonus = levelProximityBonus;
        this.divergenceBonus = divergenceBonus;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
        this.proximityFunction = new DefaultPriceProximityFunction();
    }

    /**
     * Упрощённый конструктор с разумными значениями по умолчанию.
     */
    public FlexibleWeightCalculator() {
        this(0.4, 0.5, 0.15, 0.2, 0.2, 0.8, new DefaultPriceProximityFunction());
    }

    @Override
    public WeightFactors compute(Upside levelsUpside,
                                 Upside volumeUpside,
                                 List<Candle> recentCandles,
                                 LevelPair levelPair) {
        if (recentCandles == null || recentCandles.isEmpty()) {
            return new WeightFactors(0.5, 0.5);
        }
        Candle lastCandle = recentCandles.get(recentCandles.size() - 1);

        // Фактор 1: абсолютная сила объёмного сигнала (0..1)
        double absVolumeSignal = Math.abs(volumeUpside.signal());

        // Степень близости к любому из уровней (берём максимум, можно и сумму)
        double proximityRes = proximityFunction.compute(lastCandle, levelPair.resistance(), recentCandles);
        double proximitySup = proximityFunction.compute(lastCandle, levelPair.support(), recentCandles);
        double maxProximity = Math.max(proximityRes, proximitySup);

        // Фактор 3: дивергенция (разные знаки)
        double divergenceDegree = Math.max(0, -levelsUpside.signal() * volumeUpside.signal());

        // Вычисляем вес объёмов (без нормализации)
        double volumeWeight = baseVolumeWeight
            + absVolumeSignalWeight * absVolumeSignal
            + levelProximityBonus * maxProximity
            + divergenceBonus * divergenceDegree;

        // Ограничиваем
        volumeWeight = clamp(volumeWeight, minWeight, maxWeight);

        // Вес уровней — остаток (ещё не нормализован)
        double levelsWeight = 1.0 - volumeWeight;
        // Но и его ограничиваем (на случай, если volumeWeight вышел за пределы)
        levelsWeight = clamp(levelsWeight, minWeight, maxWeight);

        // Нормализуем сумму к 1.0
        double total = levelsWeight + volumeWeight;
        if (total <= 0) {
            return new WeightFactors(0.5, 0.5);
        }
        return new WeightFactors(levelsWeight / total, volumeWeight / total);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
