package com.siberalt.singularity.strategy.signal.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.linear.LinearLevelDetector;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.level.selector.LevelPairSelector;
import com.siberalt.singularity.strategy.level.selector.StrongestLevelPairSelector;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record KeyLevelsSignalSource(LevelDetector supportLevelDetector,
                                        LevelDetector resistanceLevelDetector,
                                        LevelBasedSignalSource levelBasedSignalSource,
                                        LevelPairSelector levelSelector,
                                        SignalSource fallbackSignalSource) implements SignalSource {
    public KeyLevelsSignalSource(
        LevelDetector supportLevelDetector,
        LevelDetector resistanceLevelDetector,
        LevelBasedSignalSource levelBasedSignalSource,
        LevelPairSelector levelSelector,
        SignalSource fallbackSignalSource
    ) {
        this.supportLevelDetector = Objects.requireNonNull(supportLevelDetector);
        this.resistanceLevelDetector = Objects.requireNonNull(resistanceLevelDetector);
        this.levelBasedSignalSource = Objects.requireNonNull(levelBasedSignalSource);
        this.levelSelector = Objects.requireNonNull(levelSelector);
        this.fallbackSignalSource = Objects.requireNonNull(fallbackSignalSource);
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Signal.NEUTRAL;
        }

        List<Level<Double>> supportLevels = supportLevelDetector.detect(lastCandles);
        List<Level<Double>> resistanceLevels = resistanceLevelDetector.detect(lastCandles);

        List<LevelPair> selectedLevels = levelSelector.select(resistanceLevels, supportLevels, lastCandles);

        if (selectedLevels.isEmpty()) {
            return fallbackSignalSource.calculate(lastCandles);
        }

        if (selectedLevels.size() == 1) {
            LevelPair levelPair = selectedLevels.get(0);

            Signal signal = levelBasedSignalSource.calculate(levelPair, lastCandles);

            return signal.strength() > 0 ? signal: Signal.NEUTRAL;
        }

        List<LevelPairSignal> signals = selectedLevels.stream()
            .map(lp -> calculateLevelPairSignal(lp, lastCandles))
            .toList();

        double totalWeight = signals.stream()
            .mapToDouble(lp -> lp.weight)
            .sum();

        if (totalWeight <= 0) {
            return Signal.NEUTRAL;
        }

        double combinedSignal = 0;
        double combinedStrength = 0;

        for (LevelPairSignal lpSignal : signals) {
            double weightFraction = lpSignal.weight / totalWeight;
            combinedSignal += lpSignal.signal.confidence() * weightFraction;
            combinedStrength += lpSignal.signal.strength() * weightFraction;
        }

        return new Signal(combinedSignal, combinedStrength);
    }

    private record LevelPairSignal(LevelPair levelPair, Signal signal, double weight) {
    }

    private LevelPairSignal calculateLevelPairSignal(LevelPair levelPair, List<Candle> lastCandles) {
        Signal signal = levelBasedSignalSource.calculate(levelPair, lastCandles);

        return new LevelPairSignal(
            levelPair,
            signal,
            levelPair.resistance().strength() + levelPair.support().strength()
        );
    }

    public static KeyLevelsSignalSource createLinear(long frameSize, double neighborhoodRatio) {
        return createLinear(
            frameSize,
            neighborhoodRatio,
            neighborhoodRatio,
            new BasicLevelBasedSignalSource()
        );
    }

    public static KeyLevelsSignalSource createLinear(
        long frameSize,
        double resistanceNeighborhoodRatio,
        double supportNeighborhoodRatio,
        LevelBasedSignalSource levelBasedSignalSource
    ) {
        Function<Candle, Double> priceExtractor = Candle::getCloseAsDouble;

        return createLinear(
            frameSize,
            resistanceNeighborhoodRatio,
            supportNeighborhoodRatio,
            levelBasedSignalSource,
            priceExtractor,
            lastCandles -> Signal.NEUTRAL
        );
    }

    public static KeyLevelsSignalSource createLinear(
        long frameSize,
        double resistanceNeighborhoodRatio,
        double supportNeighborhoodRatio,
        LevelBasedSignalSource levelBasedSignalSource,
        Function<Candle, Double> priceExtractor,
        SignalSource fallbackSignalSource
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

        return new KeyLevelsSignalSource(
            supportLevelDetector,
            resistanceLevelDetector,
            levelBasedSignalSource,
            new StrongestLevelPairSelector(1),
            fallbackSignalSource
        );
    }
}
