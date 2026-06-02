package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.linear.LinearLevelDetector;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.level.selector.LevelPairSelector;
import com.siberalt.singularity.strategy.level.selector.StrongestLevelPairSelector;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record KeyLevelsUpsideCalculator(LevelDetector supportLevelDetector,
                                        LevelDetector resistanceLevelDetector,
                                        LevelBasedUpsideCalculator levelBasedUpsideCalculator,
                                        LevelPairSelector levelSelector,
                                        UpsideCalculator fallbackUpsideCalculator) implements UpsideCalculator {
    public KeyLevelsUpsideCalculator(
        LevelDetector supportLevelDetector,
        LevelDetector resistanceLevelDetector,
        LevelBasedUpsideCalculator levelBasedUpsideCalculator,
        LevelPairSelector levelSelector,
        UpsideCalculator fallbackUpsideCalculator
    ) {
        this.supportLevelDetector = Objects.requireNonNull(supportLevelDetector);
        this.resistanceLevelDetector = Objects.requireNonNull(resistanceLevelDetector);
        this.levelBasedUpsideCalculator = Objects.requireNonNull(levelBasedUpsideCalculator);
        this.levelSelector = Objects.requireNonNull(levelSelector);
        this.fallbackUpsideCalculator = Objects.requireNonNull(fallbackUpsideCalculator);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        List<Level<Double>> supportLevels = supportLevelDetector.detect(lastCandles);
        List<Level<Double>> resistanceLevels = resistanceLevelDetector.detect(lastCandles);

        List<LevelPair> selectedLevels = levelSelector.select(resistanceLevels, supportLevels, lastCandles);

        if (selectedLevels.isEmpty()) {
            return fallbackUpsideCalculator.calculate(lastCandles);
        }

        if (selectedLevels.size() == 1) {
            LevelPair levelPair = selectedLevels.get(0);

            Upside upside = levelBasedUpsideCalculator.calculate(levelPair, lastCandles);

            return upside.strength() > 0 ? upside: Upside.NEUTRAL;
        }

        List<LevelPairUpside> upsides = selectedLevels.stream()
            .map(lp -> calculateLevelPairUpside(lp, lastCandles))
            .toList();

        double totalWeight = upsides.stream()
            .mapToDouble(lp -> lp.weight)
            .sum();

        if (totalWeight <= 0) {
            return Upside.NEUTRAL;
        }

        double combinedSignal = 0;
        double combinedStrength = 0;

        for (LevelPairUpside lpUpside : upsides) {
            double weightFraction = lpUpside.weight / totalWeight;
            combinedSignal += lpUpside.upside.signal() * weightFraction;
            combinedStrength += lpUpside.upside.strength() * weightFraction;
        }

        return new Upside(combinedSignal, combinedStrength);
    }

    private record LevelPairUpside(LevelPair levelPair, Upside upside, double weight) {
    }

    private LevelPairUpside calculateLevelPairUpside(LevelPair levelPair, List<Candle> lastCandles) {
        Upside upside = levelBasedUpsideCalculator.calculate(levelPair, lastCandles);

        return new LevelPairUpside(
            levelPair,
            upside,
            levelPair.resistance().strength() + levelPair.support().strength()
        );
    }

    public static KeyLevelsUpsideCalculator createLinear(long frameSize, double neighborhoodRatio) {
        return createLinear(
            frameSize,
            neighborhoodRatio,
            neighborhoodRatio,
            new BasicLevelBasedUpsideCalculator()
        );
    }

    public static KeyLevelsUpsideCalculator createLinear(
        long frameSize,
        double resistanceNeighborhoodRatio,
        double supportNeighborhoodRatio,
        LevelBasedUpsideCalculator levelBasedUpsideCalculator
    ) {
        Function<Candle, Double> priceExtractor = Candle::getCloseAsDouble;

        return createLinear(
            frameSize,
            resistanceNeighborhoodRatio,
            supportNeighborhoodRatio,
            levelBasedUpsideCalculator,
            priceExtractor,
            lastCandles -> Upside.NEUTRAL
        );
    }

    public static KeyLevelsUpsideCalculator createLinear(
        long frameSize,
        double resistanceNeighborhoodRatio,
        double supportNeighborhoodRatio,
        LevelBasedUpsideCalculator levelBasedUpsideCalculator,
        Function<Candle, Double> priceExtractor,
        UpsideCalculator fallbackUpsideCalculator
    ) {
        // Load candles or perform any necessary initialization here
        var supportLevelDetector = LinearLevelDetector.createSupport(
            frameSize,
            supportNeighborhoodRatio,
            priceExtractor
        );
        var resistanceLevelDetector = LinearLevelDetector.createResistance(
            frameSize,
            resistanceNeighborhoodRatio,
            priceExtractor
        );

        return new KeyLevelsUpsideCalculator(
            supportLevelDetector,
            resistanceLevelDetector,
            levelBasedUpsideCalculator,
            new StrongestLevelPairSelector(1),
            fallbackUpsideCalculator
        );
    }
}
