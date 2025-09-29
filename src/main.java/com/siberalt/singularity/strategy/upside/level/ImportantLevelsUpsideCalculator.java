package com.siberalt.singularity.strategy.upside.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.linear.LinearLevelDetector;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class ImportantLevelsUpsideCalculator implements UpsideCalculator {
    private LevelDetector<Double> supportLevelDetector;
    private LevelDetector<Double> resistanceLevelDetector;
    private LevelBasedUpsideCalculator levelBasedUpsideCalculator;

    public ImportantLevelsUpsideCalculator(
        LevelDetector<Double> supportLevelDetector,
        LevelDetector<Double> resistanceLevelDetector,
        LevelBasedUpsideCalculator levelBasedUpsideCalculator
    ) {
        this.supportLevelDetector = supportLevelDetector;
        this.resistanceLevelDetector = resistanceLevelDetector;
        this.levelBasedUpsideCalculator = levelBasedUpsideCalculator;
    }

    public ImportantLevelsUpsideCalculator setLevelBasedUpsideCalculator(LevelBasedUpsideCalculator levelBasedUpsideCalculator) {
        this.levelBasedUpsideCalculator = levelBasedUpsideCalculator;
        return this;
    }

    public ImportantLevelsUpsideCalculator setSupportLevelDetector(LevelDetector<Double> supportLevelDetector) {
        this.supportLevelDetector = supportLevelDetector;
        return this;
    }

    public ImportantLevelsUpsideCalculator setResistanceLevelDetector(LevelDetector<Double> resistanceLevelDetector) {
        this.resistanceLevelDetector = resistanceLevelDetector;
        return this;
    }

    @Override
    public Upside calculate(String instrumentId, Instant currentTime, List<Candle> lastCandles) {
        List<? extends Level<Double>> supportLevels = supportLevelDetector.detect(lastCandles);
        List<? extends Level<Double>> resistanceLevels = resistanceLevelDetector.detect(lastCandles);

        Level<Double> currentSupport = supportLevels.stream()
            .max(Comparator.comparingLong(Level::getIndexTo))
            .orElse(null);

        Level<Double> currentResistance = resistanceLevels.stream()
            .max(Comparator.comparingLong(Level::getIndexTo))
            .orElse(null);

        // Example calculation: return the difference between resistance and support as a percentage
        if (currentSupport != null && currentResistance != null) {
            double currentPrice = lastCandles.get(lastCandles.size() - 1).getClosePrice().toBigDecimal().doubleValue();

            return levelBasedUpsideCalculator.calculate(currentPrice, currentResistance, currentSupport);
        }

        return new Upside(0, 0);
    }

    public static ImportantLevelsUpsideCalculator createLinear(long frameSize, double neighborhoodRatio) {
        return createLinear(
            frameSize,
            neighborhoodRatio,
            neighborhoodRatio,
            new BasicLevelBasedUpsideCalculator()
        );
    }

    public static ImportantLevelsUpsideCalculator createLinear(
        long frameSize,
        double neighborhoodRatio,
        LevelBasedUpsideCalculator levelBasedUpsideCalculator
    ) {
        return createLinear(
            frameSize,
            neighborhoodRatio,
            neighborhoodRatio,
            levelBasedUpsideCalculator
        );
    }

    public static ImportantLevelsUpsideCalculator createLinear(
        long frameSize,
        double resistanceNeighborhoodRatio,
        double supportNeighborhoodRatio,
        LevelBasedUpsideCalculator levelBasedUpsideCalculator
    ) {
        Function<Candle, Double> priceExtractor = c -> c.getClosePrice().toBigDecimal().doubleValue();

        return createLinear(
            frameSize,
            resistanceNeighborhoodRatio,
            supportNeighborhoodRatio,
            levelBasedUpsideCalculator,
            priceExtractor
        );
    }

    public static ImportantLevelsUpsideCalculator createLinear(
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

        return new ImportantLevelsUpsideCalculator(
            supportLevelDetector,
            resistanceLevelDetector,
            levelBasedUpsideCalculator
        );
    }
}
