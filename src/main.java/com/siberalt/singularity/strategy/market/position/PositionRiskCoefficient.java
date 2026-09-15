package com.siberalt.singularity.strategy.market.position;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.LastExtremeLocator;
import com.siberalt.singularity.strategy.market.MarketCoefficient;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * How far the open position is under water, counted in volatilities.
 * <p>
 * Read as a number rather than delivered as a verdict, which is what lets a calculator do something
 * other than close: weigh the risk against whatever else it is listening to. A reading of eight says
 * the price sits eight volatilities the wrong side of the reference whether the position is long or
 * short, a negative reading says the position is ahead, and a flat book reads zero.
 * <p>
 * The reference is the better of the average entry price and the furthest the price has run in the
 * position's favour since it was opened - the high water mark for a long, the low for a short. Hand
 * it locators that find nothing and the reference stays at the entry price, which is the difference
 * between a stop that takes profits back and one that fires only on a position that is losing.
 * <p>
 * Being about a position rather than about the market, it is the one {@link MarketCoefficient} that
 * breaks that interface's own rule - the reading is not something the market is doing. What it is for
 * is the same, though: something measured, that a calculator weighs by, rather than a second opinion
 * on where the price is going.
 * <p>
 * The volatility is asked for in the price's own units - {@link ATRVolatilityCalculator} by default,
 * as elsewhere in the package. One measured as a share of price would make the reading smaller by a
 * factor of the price and the coefficient silent on everything.
 */
public class PositionRiskCoefficient implements MarketCoefficient {
    private final String accountId;
    private final EntryPriceCalculator entryPriceCalculator;
    private final VolatilityCalculator volatilityCalculator;
    private final ExtremeLocator maxLocator;
    private final ExtremeLocator minLocator;
    private final PriceExtractor priceExtractor;

    public PositionRiskCoefficient(
        String accountId,
        EntryPriceCalculator entryPriceCalculator,
        VolatilityCalculator volatilityCalculator,
        ExtremeLocator maxLocator,
        ExtremeLocator minLocator,
        PriceExtractor priceExtractor
    ) {
        if (entryPriceCalculator == null || volatilityCalculator == null
            || maxLocator == null || minLocator == null || priceExtractor == null) {
            throw new IllegalArgumentException("A reading needs a position, a volatility and a price to read");
        }

        this.accountId = accountId;
        this.entryPriceCalculator = entryPriceCalculator;
        this.volatilityCalculator = volatilityCalculator;
        this.maxLocator = maxLocator;
        this.minLocator = minLocator;
        this.priceExtractor = priceExtractor;
    }

    /** Defaults: an hourly-style ATR of fourteen, close prices, and extremes over a vicinity of three. */
    public PositionRiskCoefficient(String accountId, EntryPriceCalculator entryPriceCalculator) {
        this(
            accountId,
            entryPriceCalculator,
            new ATRVolatilityCalculator(14),
            LastExtremeLocator.ofMaximums(3, Candle::getCloseAsDouble),
            LastExtremeLocator.ofMinimums(3, Candle::getCloseAsDouble),
            Candle::close
        );
    }

    public PositionRiskCoefficient(
        String accountId,
        EntryPriceCalculator entryPriceCalculator,
        VolatilityCalculator volatilityCalculator
    ) {
        this(
            accountId,
            entryPriceCalculator,
            volatilityCalculator,
            LastExtremeLocator.ofMaximums(3, Candle::getCloseAsDouble),
            LastExtremeLocator.ofMinimums(3, Candle::getCloseAsDouble),
            Candle::close
        );
    }

    @Override
    public double of(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return 0;
        }

        EntryPrice position = positionIn(lastCandles);

        if (position.isEmpty() || position.quantity() == 0) {
            return 0;
        }

        double volatility = volatilityCalculator.calculate(lastCandles);
        double current = priceExtractor.extract(lastCandles.getLast()).toDouble();
        double reference = referenceOf(position, lastCandles);
        // A long is hurt by a fall and a short by a rise; the reading is about the position, so it
        // says the same thing either way round.
        double against = position.quantity() > 0 ? reference - current : current - reference;

        return against / (volatility == 0 ? Double.MIN_VALUE : volatility);
    }

    /** The position this reading is about, which is the one held in the instrument these candles are of. */
    public EntryPrice positionIn(List<Candle> lastCandles) {
        return entryPriceCalculator.calculate(accountId, lastCandles.getFirst().instrumentUid());
    }

    private double referenceOf(EntryPrice position, List<Candle> lastCandles) {
        double average = position.averagePrice().toDouble();
        Instant since = position.timePointRange().fromTime();

        if (position.quantity() > 0) {
            return extremeSince(maxLocator.locate(lastCandles), since)
                .map(high -> Math.max(average, high))
                .orElse(average);
        }

        return extremeSince(minLocator.locate(lastCandles), since)
            .map(low -> Math.min(average, low))
            .orElse(average);
    }

    private Optional<Double> extremeSince(List<Candle> extremes, Instant since) {
        if (extremes.isEmpty() || extremes.getLast().getTime().isBefore(since)) {
            return Optional.empty();
        }

        return Optional.of(priceExtractor.extract(extremes.getLast()).toDouble());
    }
}
