package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;

public class AdaptiveUpsideCalculator implements LevelBasedUpsideCalculator {
    record WeightFactors(double levelsWeight, double volumeWeight) {
    }

    public final double BASE_LEVELS_WEIGHT = 0.6;    // Базовый вес уровней
    public final double BASE_VOLUME_WEIGHT = 0.4;    // Базовый вес объемов

    private final LevelBasedUpsideCalculator levelsCalculator;
    private final UpsideCalculator volumeCalculator;

    public AdaptiveUpsideCalculator(
        LevelBasedUpsideCalculator levelsCalculator,
        UpsideCalculator volumeCalculator
    ) {
        this.levelsCalculator = levelsCalculator;
        this.volumeCalculator = volumeCalculator;
    }

    @Override
    public Upside calculate(LevelPair levelPair, List<Candle> recentCandles) {
        Upside volumeUpside = volumeCalculator.calculate(recentCandles);

        if (LevelPair.EMPTY.equals(levelPair)) {
            return volumeUpside;
        }

        Upside levelsUpside = levelsCalculator.calculate(levelPair, recentCandles);

        // 3. Динамически адаптируем веса на основе контекста
        WeightFactors weightFactors = calculateAdaptiveWeights(
            levelsUpside, volumeUpside, recentCandles, levelPair
        );

        // 4. Комбинируем сигналы
        double combinedSignal = weightFactors.levelsWeight * levelsUpside.signal()
            + weightFactors.volumeWeight * volumeUpside.signal();

        double combinedStrength = weightFactors.levelsWeight * levelsUpside.strength()
            + weightFactors.volumeWeight * volumeUpside.strength();

        return new Upside(combinedSignal, combinedStrength);
    }

    private boolean isNearKeyLevel(Candle currentCandle, long currentCandleIndex, Level<Double> level) {
        double levelPrice = level.function().apply((double) currentCandleIndex);
        double threshold = levelPrice * 0.005; // 0.5% threshold
        return Math.abs(currentCandle.getTypicalPrice().toDouble() - levelPrice) <= threshold;
    }

    private boolean hasStrongDivergence(Upside levelsUpside, Upside volumeUpside) {
        // Проверка на сильную дивергенцию сигналов
        return levelsUpside.signal() * volumeUpside.signal() < 0 && Math.abs(volumeUpside.signal()) > 0.5;
    }

    private WeightFactors calculateAdaptiveWeights(
        Upside levels,
        Upside volume,
        List<Candle> recentCandles,
        LevelPair levelPair
    ) {
        // Инициализация базовых значений
        var resistance = levelPair.resistance();
        var support = levelPair.support();

        double levelsWeight = BASE_LEVELS_WEIGHT;
        double volumeWeight = BASE_VOLUME_WEIGHT;

        Candle currentCandle = recentCandles.get(recentCandles.size() - 1);
        long currentCandleIndex = currentCandle.getIndex();

        // 🔄 Правила адаптации весов

        // 3. Около ключевых уровней - баланс смещается в зависимости от объемного подтверждения
        if (
            isNearKeyLevel(currentCandle, currentCandleIndex, resistance)
            || isNearKeyLevel(currentCandle, currentCandleIndex, support)
        ) {
            if (volume.signal() > 0.6) {
                // Сильное объемное подтверждение - увеличиваем вес объемов
                volumeWeight += 0.2;
                levelsWeight -= 0.2;
            } else {
                // Слабое подтверждение - доверяем больше уровням
                levelsWeight += 0.1;
                volumeWeight -= 0.1;
            }
        }

        // 4. При сильных объемных сигналах увеличиваем их вес
        if (Math.abs(volume.signal()) > 0.7) {
            volumeWeight += 0.1;
            levelsWeight -= 0.1;
        }

        // 5. При дивергенции сигналов - доверяем объемам больше
        if (hasStrongDivergence(levels, volume)) {
            volumeWeight += 0.15;
            levelsWeight -= 0.15;
        }

        // Гарантируем минимальные веса
        levelsWeight = Math.max(0.2, Math.min(0.8, levelsWeight));
        volumeWeight = Math.max(0.2, Math.min(0.8, volumeWeight));

        // Нормализуем чтобы сумма была 1.0
        double total = levelsWeight + volumeWeight;

        return new WeightFactors(levelsWeight / total, volumeWeight / total);
    }
}
