package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;
import java.util.ArrayList;

public class CompositeFactorSignalSource implements SignalSource {
    public record WeightedCalculator(SignalSource calculator, double weight) {
    }

    private final List<WeightedCalculator> weightedCalculators;

    private TypePolicy type = TypePolicy.UNDECIDED;

    private BalancePolicy balance = BalancePolicy.UNDEFINED;

    public CompositeFactorSignalSource(List<WeightedCalculator> weightedCalculators) {
        if (weightedCalculators == null || weightedCalculators.isEmpty()) {
            throw new IllegalArgumentException("At least one calculator is required.");
        }
        this.weightedCalculators = weightedCalculators;
    }

    /**
     * О чём получается сигнал этого состава. По умолчанию ни о чём - {@link SignalType#UNSPECIFIED}:
     * делегаты тип не подскажут, а сливать несогласные типы нечем, так что это решение собравшего.
     */
    public CompositeFactorSignalSource setType(SignalType type) {
        return setType(TypePolicy.fixed(type));
    }

    /** Тип как функция расклада - например {@link TypePolicy#BY_SIDE} для книги без шортов. */
    public CompositeFactorSignalSource setType(TypePolicy type) {
        if (type == null) {
            throw new IllegalArgumentException("Нет политики, которой определять тип");
        }

        this.type = type;

        return this;
    }

    /**
     * Какую долю счёта просить. По умолчанию состав её не называет, и размер считает
     * {@link com.siberalt.singularity.strategy.impl.quantity.TradeQuantity} от уверенности, как раньше;
     * {@link BalancePolicy#AGREEING_WEIGHT} вместо этого берёт долю согласного веса.
     */
    public CompositeFactorSignalSource setBalance(BalancePolicy balance) {
        if (balance == null) {
            throw new IllegalArgumentException("Нет политики, которой определять долю счёта");
        }

        this.balance = balance;

        return this;
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

        double positiveWeight = 0;
        double negativeWeight = 0;

        for (WeightedCalculator wc : weightedCalculators) {
            double normalizedWeight = wc.weight() / weightSum;
            Signal signal = wc.calculator().calculate(recentCandles);
            totalSignal += signal.confidence() * normalizedWeight;
            totalStrength += signal.strength() * normalizedWeight;
            totalWeight += normalizedWeight;

            if (signal.confidence() > 0) {
                positiveWeight += normalizedWeight;
            } else if (signal.confidence() < 0) {
                negativeWeight += normalizedWeight;
            }
        }

        if (Math.abs(totalWeight) < 1e-10) {
            return Signal.NEUTRAL;
        }

        Votes votes = new Votes(
            totalSignal / totalWeight,
            totalStrength / totalWeight,
            totalWeight,
            positiveWeight,
            negativeWeight
        );

        return new Signal(type.of(votes), votes.confidence(), votes.strength(), balance.of(votes));
    }

    public static class Builder {
        private final List<WeightedCalculator> weightedCalculators = new ArrayList<>();

        public Builder addSource(SignalSource calculator, double weight) {
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
