package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WeightedVolatilityCalculator implements VolatilityCalculator {
    private final List<CalculatorWeight> components = new ArrayList<>();

    public WeightedVolatilityCalculator add(VolatilityCalculator calculator, double weight) {
        components.add(new CalculatorWeight(calculator, weight));
        return this;
    }

    @Override
    public double calculate(List<Candle> candles) {
        if (components.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (CalculatorWeight cw : components) {
            total += cw.calculator.calculate(candles) * cw.weight;
        }
        return total;
    }

    private record CalculatorWeight(VolatilityCalculator calculator, double weight) {
        private CalculatorWeight(VolatilityCalculator calculator, double weight) {
            this.calculator = Objects.requireNonNull(calculator);
            this.weight = weight;
        }
    }
}
