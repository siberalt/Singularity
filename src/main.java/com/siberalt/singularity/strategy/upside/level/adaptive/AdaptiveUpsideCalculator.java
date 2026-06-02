package com.siberalt.singularity.strategy.upside.level.adaptive;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.upside.level.LevelBasedUpsideCalculator;

import java.util.List;

public class AdaptiveUpsideCalculator implements LevelBasedUpsideCalculator {
    private final LevelBasedUpsideCalculator levelsCalculator;
    private final UpsideCalculator volumeCalculator;
    private final WeightCalculator weightCalculator;

    public AdaptiveUpsideCalculator(LevelBasedUpsideCalculator levels,
                                    UpsideCalculator volume,
                                    WeightCalculator weightCalculator) {
        this.levelsCalculator = levels;
        this.volumeCalculator = volume;
        this.weightCalculator = weightCalculator;
    }

    public AdaptiveUpsideCalculator(LevelBasedUpsideCalculator levels, UpsideCalculator volume) {
        this(levels, volume, new FlexibleWeightCalculator());
    }

    @Override
    public Upside calculate(LevelPair levelPair, List<Candle> candles) {
        Upside levelsUp = levelsCalculator.calculate(levelPair, candles);
        Upside volumeUp = volumeCalculator.calculate(candles);
        WeightFactors wf = weightCalculator.compute(levelsUp, volumeUp, candles, levelPair);
        double signal = wf.levelsWeight() * levelsUp.signal() + wf.volumeWeight() * volumeUp.signal();
        double strength = wf.levelsWeight() * levelsUp.strength() + wf.volumeWeight() * volumeUp.strength();
        return new Upside(signal, strength);
    }
}
