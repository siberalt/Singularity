package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.linear.LinearLevelDetector;
import com.siberalt.singularity.strategy.level.selector.BasicLevelSelector;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.level.selector.LevelSelector;
import com.siberalt.singularity.strategy.market.CumulativeCandleIndexProvider;
import com.siberalt.singularity.strategy.market.DefaultCandleIndexProvider;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;
import java.util.function.Function;

public class KeyLevelsUpsideCalculator implements UpsideCalculator {
    private record LevelPairUpside(
        LevelPair levelPair,
        Upside upside,
        double weight
    ) {
    }

    private LevelDetector<Double> supportLevelDetector;
    private LevelDetector<Double> resistanceLevelDetector;
    private LevelBasedUpsideCalculator levelBasedUpsideCalculator;
    private CumulativeCandleIndexProvider candleIndexProvider = new DefaultCandleIndexProvider();
    private LevelSelector levelSelector = new BasicLevelSelector();

    public KeyLevelsUpsideCalculator(
        LevelDetector<Double> supportLevelDetector,
        LevelDetector<Double> resistanceLevelDetector,
        LevelBasedUpsideCalculator levelBasedUpsideCalculator
    ) {
        this.supportLevelDetector = supportLevelDetector;
        this.resistanceLevelDetector = resistanceLevelDetector;
        this.levelBasedUpsideCalculator = levelBasedUpsideCalculator;
    }

    public KeyLevelsUpsideCalculator setLevelSelector(LevelSelector levelSelector) {
        this.levelSelector = levelSelector;
        return this;
    }

    public KeyLevelsUpsideCalculator setCandleIndexProvider(CumulativeCandleIndexProvider candleIndexProvider) {
        this.candleIndexProvider = candleIndexProvider;
        return this;
    }

    public KeyLevelsUpsideCalculator setLevelBasedUpsideCalculator(LevelBasedUpsideCalculator levelBasedUpsideCalculator) {
        this.levelBasedUpsideCalculator = levelBasedUpsideCalculator;
        return this;
    }

    public KeyLevelsUpsideCalculator setSupportLevelDetector(LevelDetector<Double> supportLevelDetector) {
        this.supportLevelDetector = supportLevelDetector;
        return this;
    }

    public KeyLevelsUpsideCalculator setResistanceLevelDetector(LevelDetector<Double> resistanceLevelDetector) {
        this.resistanceLevelDetector = resistanceLevelDetector;
        return this;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        candleIndexProvider.accumulate(lastCandles);
        List<Level<Double>> supportLevels = supportLevelDetector.detect(lastCandles, candleIndexProvider);
        List<Level<Double>> resistanceLevels = resistanceLevelDetector.detect(lastCandles, candleIndexProvider);

        List<LevelPair> selectedLevels = levelSelector.select(
            resistanceLevels,
            supportLevels,
            lastCandles,
            candleIndexProvider
        );

        if (selectedLevels.isEmpty()) {
            return new Upside(0, 0);
        } else if (selectedLevels.size() == 1) {
            LevelPair levelPair = selectedLevels.get(0);

            return levelBasedUpsideCalculator.calculate(
                levelPair.resistance(),
                levelPair.support(),
                lastCandles,
                candleIndexProvider
            );
        }

        List<LevelPairUpside> upsides = selectedLevels.stream()
            .map(lp -> calculateLevelPairUpside(lp, lastCandles))
            .toList();

        double totalWeight = upsides.stream()
            .mapToDouble(lp -> lp.weight)
            .sum();

        double combinedSignal = 0;
        double combinedStrength = 0;

        for (LevelPairUpside lpUpside : upsides) {
            double weightFraction = lpUpside.weight / totalWeight;
            combinedSignal += lpUpside.upside.signal() * weightFraction;
            combinedStrength += lpUpside.upside.strength() * weightFraction;
        }

        return new Upside(combinedSignal, combinedStrength);
    }

    private LevelPairUpside calculateLevelPairUpside(LevelPair levelPair, List<Candle> lastCandles) {
        Upside upside = levelBasedUpsideCalculator.calculate(
            levelPair.resistance(),
            levelPair.support(),
            lastCandles,
            candleIndexProvider
        );

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
        Function<Candle, Double> priceExtractor = c -> c.getClosePrice().toDouble();

        return createLinear(
            frameSize,
            resistanceNeighborhoodRatio,
            supportNeighborhoodRatio,
            levelBasedUpsideCalculator,
            priceExtractor
        );
    }

    public static KeyLevelsUpsideCalculator createLinear(
        long frameSize,
        double resistanceNeighborhoodRatio,
        double supportNeighborhoodRatio,
        LevelBasedUpsideCalculator levelBasedUpsideCalculator,
        Function<Candle, Double> priceExtractor
    ) {
        // Load candles or perform any necessary initialization here
        var supportLevelDetector = LinearLevelDetector.createSupport(
            frameSize,
            resistanceNeighborhoodRatio,
            priceExtractor
        );
        var resistanceLevelDetector = LinearLevelDetector.createResistance(
            frameSize,
            supportNeighborhoodRatio,
            priceExtractor
        );

        return new KeyLevelsUpsideCalculator(
            supportLevelDetector,
            resistanceLevelDetector,
            levelBasedUpsideCalculator
        );
    }
}
