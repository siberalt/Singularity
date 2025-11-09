package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public class CompositeFactorUpsideCalculator implements UpsideCalculator {
    public record WeightedCalculator(UpsideCalculator calculator, double weight) {}

    private final List<WeightedCalculator> weightedCalculators;

    public CompositeFactorUpsideCalculator(List<WeightedCalculator> weightedCalculators) {
        if (weightedCalculators == null || weightedCalculators.isEmpty()) {
            throw new IllegalArgumentException("At least one calculator is required.");
        }
        this.weightedCalculators = weightedCalculators;
    }

    @Override
    public Upside calculate(List<Candle> recentCandles) {
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
            Upside upside = wc.calculator().calculate(recentCandles);
            totalSignal += upside.signal() * normalizedWeight;
            totalStrength += upside.strength() * normalizedWeight;
            totalWeight += normalizedWeight;
        }

        if (Math.abs(totalWeight) < 1e-10) {
            return new Upside(0, 0);
        }

        return new Upside(totalSignal / totalWeight, totalStrength / totalWeight);
    }
}
