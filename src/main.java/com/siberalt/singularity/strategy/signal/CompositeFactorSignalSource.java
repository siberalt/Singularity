package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;
import java.util.ArrayList;

public class CompositeFactorSignalSource implements SignalSource {
    public record WeightedCalculator(SignalSource calculator, double weight) {
    }

    private final List<WeightedCalculator> weightedCalculators;

    public CompositeFactorSignalSource(List<WeightedCalculator> weightedCalculators) {
        if (weightedCalculators == null || weightedCalculators.isEmpty()) {
            throw new IllegalArgumentException("At least one calculator is required.");
        }
        this.weightedCalculators = weightedCalculators;
    }

    @Override
    public Signal calculate(List<Candle> recentCandles) {
        double totalSignal = 0;
        double totalStrength = 0;
        double totalWeight = 0;

        // Normalize weights
        double weightSum = weightedCalculators.stream()
            .mapToDouble(WeightedCalculator::weight)
            .sum();

        if (Math.abs(weightSum) < 1e-10) {
            throw new IllegalArgumentException("Sum of weights cannot be zero.");
        }

        for (WeightedCalculator wc : weightedCalculators) {
            double normalizedWeight = wc.weight() / weightSum;
            Signal signal = wc.calculator().calculate(recentCandles);
            totalSignal += signal.confidence() * normalizedWeight;
            totalStrength += signal.strength() * normalizedWeight;
            totalWeight += normalizedWeight;
        }

        if (Math.abs(totalWeight) < 1e-10) {
            return Signal.NEUTRAL;
        }

        return new Signal(totalSignal / totalWeight, totalStrength / totalWeight);
    }

    public static class Builder {
        private final List<WeightedCalculator> weightedCalculators = new ArrayList<>();

        public Builder addCalculator(SignalSource calculator, double weight) {
            weightedCalculators.add(new WeightedCalculator(calculator, weight));
            return this;
        }

        public CompositeFactorSignalSource build() {
            return new CompositeFactorSignalSource(weightedCalculators);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static WeightedCalculator newWeightedCalculator(SignalSource signalSource, double weight) {
        return new WeightedCalculator(signalSource, weight);
    }
}
