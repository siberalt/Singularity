package com.siberalt.singularity.strategy.upside;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCoefficient;

import java.util.List;

/**
 * Asks every calculator it holds and weighs their answers by what the market is doing, the weights
 * moving with the reading rather than jumping at a boundary.
 * <p>
 * The same bet as {@link RangeSwitchUpsideCalculator}, on the same kind of coefficient, without its
 * one flaw: a range hands the whole question to one calculator and changes its mind in a single
 * step. A reading hovering near a boundary - and a variance ratio near one does hover - then flips
 * the answer from a trend read to its opposite between one bar and the next. What that costs is not
 * a worse read of the market but churn: positions opened and closed on the coefficient's noise
 * rather than on anything the price did.
 * <p>
 * Weighed instead, the answer passes through zero where the switch jumped. Blend a slope with its
 * own inversion at weights {@code w} and {@code 1 - w} and what comes out is that slope times
 * {@code 2w - 1}: the same read, scaled down as the market becomes undecided and turned over only
 * once it has decided the other way. A position sized on that fades out near the boundary instead of
 * reversing across it.
 * <p>
 * With {@link #rising(double, double)} and {@link #falling(double, double)} narrowed to no width at
 * all this becomes the switch again, which is the honest way to compare the two: one setting apart,
 * not two different contraptions.
 */
public class SmoothSwitchUpsideCalculator implements UpsideCalculator {
    /**
     * How much a calculator's answer counts at this reading of the coefficient. Negative is not a
     * vote against - {@link InvertedUpsideCalculator} is what says the opposite - and counts as no
     * vote at all.
     */
    @FunctionalInterface
    public interface Weight {
        double at(double reading);
    }

    public record WeightedCalculator(UpsideCalculator calculator, Weight weight) {
        public WeightedCalculator {
            if (calculator == null || weight == null) {
                throw new IllegalArgumentException("A voice needs something to say and a say in it");
            }
        }
    }

    private final MarketCoefficient coefficient;
    private final List<WeightedCalculator> calculators;

    public SmoothSwitchUpsideCalculator(
        MarketCoefficient coefficient,
        List<WeightedCalculator> calculators
    ) {
        if (coefficient == null) {
            throw new IllegalArgumentException("Nothing to weigh by");
        }

        if (calculators == null || calculators.isEmpty()) {
            throw new IllegalArgumentException("Nothing to weigh");
        }

        this.coefficient = coefficient;
        this.calculators = List.copyOf(calculators);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        double reading = coefficient.of(lastCandles);
        double signal = 0;
        double strength = 0;
        double total = 0;

        for (WeightedCalculator weighted : calculators) {
            double weight = Math.max(0, weighted.weight().at(reading));

            if (weight == 0) {
                continue;
            }

            Upside upside = weighted.calculator().calculate(lastCandles);
            signal += weight * upside.signal();
            strength += weight * upside.strength();
            total += weight;
        }

        // Nothing weighs anything here, which is a market none of these calculators was written for.
        if (total == 0) {
            return Upside.NEUTRAL;
        }

        return new Upside(signal / total, strength / total);
    }

    public static WeightedCalculator weighted(UpsideCalculator calculator, Weight weight) {
        return new WeightedCalculator(calculator, weight);
    }

    /**
     * A weight climbing from nothing to everything as the reading passes {@code centre}, over about
     * {@code width} of the coefficient either side of it. No width makes it a step, and the whole
     * calculator a {@link RangeSwitchUpsideCalculator}.
     */
    public static Weight rising(double centre, double width) {
        if (width < 0) {
            throw new IllegalArgumentException("A width cannot be negative, got " + width);
        }

        if (width == 0) {
            return reading -> reading >= centre ? 1 : 0;
        }

        return reading -> 1 / (1 + Math.exp(-(reading - centre) / width));
    }

    /** The other half of {@link #rising(double, double)}: everything below the centre, nothing above. */
    public static Weight falling(double centre, double width) {
        Weight rising = rising(centre, width);

        return reading -> 1 - rising.at(reading);
    }
}
