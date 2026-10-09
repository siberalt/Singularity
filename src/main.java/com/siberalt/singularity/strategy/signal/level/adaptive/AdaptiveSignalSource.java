package com.siberalt.singularity.strategy.signal.level.adaptive;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;
import com.siberalt.singularity.strategy.signal.level.LevelBasedSignalSource;

import java.util.List;

public class AdaptiveSignalSource implements LevelBasedSignalSource {
    private final LevelBasedSignalSource levelsCalculator;
    private final SignalSource volumeCalculator;
    private final WeightCalculator weightCalculator;

    public AdaptiveSignalSource(LevelBasedSignalSource levels,
                                    SignalSource volume,
                                    WeightCalculator weightCalculator) {
        this.levelsCalculator = levels;
        this.volumeCalculator = volume;
        this.weightCalculator = weightCalculator;
    }

    public AdaptiveSignalSource(LevelBasedSignalSource levels, SignalSource volume) {
        this(levels, volume, new FlexibleWeightCalculator());
    }

    @Override
    public Signal calculate(LevelPair levelPair, List<Candle> candles) {
        Signal levelsUp = levelsCalculator.calculate(levelPair, candles);
        Signal volumeUp = volumeCalculator.calculate(candles);
        WeightFactors wf = weightCalculator.compute(levelsUp, volumeUp, candles, levelPair);
        double signal = wf.levelsWeight() * levelsUp.confidence() + wf.volumeWeight() * volumeUp.confidence();
        double strength = wf.levelsWeight() * levelsUp.strength() + wf.volumeWeight() * volumeUp.strength();
        return new Signal(signal, strength);
    }
}
