package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;
import java.util.ArrayList;

public class CompositeFactorUpsideCalculator implements UpsideCalculator {
    public record WeightedCalculator(UpsideCalculator calculator, double weight) {
    }

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
            return Upside.NEUTRAL;
        }

        return new Upside(totalSignal / totalWeight, totalStrength / totalWeight);
    }

    public static class Builder {
        private final List<WeightedCalculator> weightedCalculators = new ArrayList<>();

        public Builder addCalculator(UpsideCalculator calculator, double weight) {
            weightedCalculators.add(new WeightedCalculator(calculator, weight));
            return this;
        }

        public CompositeFactorUpsideCalculator build() {
            return new CompositeFactorUpsideCalculator(weightedCalculators);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static WeightedCalculator newWeightedCalculator(UpsideCalculator upsideCalculator, double weight) {
        return new WeightedCalculator(upsideCalculator, weight);
    }
}
