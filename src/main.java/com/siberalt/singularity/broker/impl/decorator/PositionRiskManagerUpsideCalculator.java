package com.siberalt.singularity.broker.impl.decorator;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.LastExtremeLocator;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.market.position.EntryPrice;
import com.siberalt.singularity.strategy.market.position.EntryPriceCalculator;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * How far a position has moved from the best price it has seen, counted in volatilities: a trailing
 * stop written as a signal.
 * <p>
 * It answers about a position rather than about the market, which makes it unlike everything else
 * that produces an {@link Upside}. With nothing open it says nothing. Holding something, the
 * reference is the better of the average entry price and the furthest the price has run in the
 * position's favour since - the high water mark for a long, the low for a short - and the answer is
 * how far the price now sits from it, divided by the volatility, and kept within a single
 * volatility either way.
 * <p>
 * So a long reads negative as the price falls back from its peak, reaching a full close signal one
 * volatility below it, and a short the other way round. What that is worth is not a question of
 * prediction and cannot be settled the way a signal's is: the thing to measure is what it does to
 * the drawdown of a strategy it is bolted onto.
 * <p>
 * The volatility is asked for in the price's own units - {@link ATRVolatilityCalculator} by default,
 * which is what the rest of the package means by volatility too. Handing it one measured as a share
 * of price would make the reading smaller by a factor of the price.
 */
public class PositionRiskManagerUpsideCalculator implements UpsideCalculator {
    private final String accountId;
    private final VolatilityCalculator volatilityCalculator; // калькулятор волатильности
    private final ExtremeLocator maxLocator; // локатор последнего максимума
    private final ExtremeLocator minLocator; // локатор последнего минимума
    private final EntryPriceCalculator entryPriceCalculator; // сервис для получения позиций
    private final PriceExtractor priceExtractor;

    /**
     * Конструктор с полной настройкой.
     *
     * @param entryPriceCalculator сервис для получения текущих позиций
     * @param volatilityCalculator калькулятор волатильности
     * @param maxLocator           локатор последнего максимума
     * @param minLocator           локатор последнего минимума
     */
    public PositionRiskManagerUpsideCalculator(
        String accountId,
        EntryPriceCalculator entryPriceCalculator,
        VolatilityCalculator volatilityCalculator,
        ExtremeLocator maxLocator,
        ExtremeLocator minLocator,
        PriceExtractor priceExtractor
    ) {
        if (volatilityCalculator == null || maxLocator == null || minLocator == null) {
            throw new IllegalArgumentException("Calculator and locators must not be null");
        }

        this.accountId = accountId;
        this.entryPriceCalculator = entryPriceCalculator;
        this.volatilityCalculator = volatilityCalculator;
        this.maxLocator = maxLocator;
        this.minLocator = minLocator;
        this.priceExtractor = priceExtractor;
    }

    /**
     * Конструктор со стандартными настройками: множитель 1.0, стандартная волатильность и экстремумы.
     */
    public PositionRiskManagerUpsideCalculator(String accountId, EntryPriceCalculator entryPriceCalculator) {
        this(
            accountId,
            entryPriceCalculator,
            new ATRVolatilityCalculator(14),
            LastExtremeLocator.ofMaximums(3, Candle::getCloseAsDouble),
            LastExtremeLocator.ofMinimums(3, Candle::getCloseAsDouble),
            Candle::close
        );
    }

    public PositionRiskManagerUpsideCalculator(String accountId,
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
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        // Получаем текущую позицию
        EntryPrice entryPrice = entryPriceCalculator.calculate(accountId, lastCandles.getFirst().instrumentUid());
        if (entryPrice.isEmpty()) {
            return Upside.NEUTRAL;
        }

        double currentPrice = priceExtractor.extract(lastCandles.getLast()).toDouble();

        // Вычисляем среднюю цену позиции
        double averageEntryPrice = entryPrice.averagePrice().toDouble();
        long positionBalance = entryPrice.quantity();

        if (positionBalance == 0) {
            return Upside.NEUTRAL;
        }

        // Вычисляем волатильность
        double volatility = volatilityCalculator.calculate(lastCandles);
        double basePrice;

        // Для long-позиции (положительный баланс)
        if (positionBalance > 0) {
            Optional<Double> lastExtremePrice = getSuitableLastExtremePrice(
                maxLocator.locate(lastCandles),
                entryPrice.timePointRange().fromTime()
            );
            basePrice = lastExtremePrice
                .map(aDouble -> Math.max(averageEntryPrice, aDouble))
                .orElse(averageEntryPrice);
        }
        // Для short-позиции (отрицательный баланс)
        else {
            Optional<Double> lastExtremePrice = getSuitableLastExtremePrice(
                minLocator.locate(lastCandles),
                entryPrice.timePointRange().fromTime()
            );
            basePrice = lastExtremePrice
                .map(aDouble -> Math.min(averageEntryPrice, aDouble))
                .orElse(averageEntryPrice);
        }

        double signal = calculateNormalizedDeviation(currentPrice, basePrice, volatility);
        double clamped = Math.min(1, Math.max(-1, signal));

        // Strength is a confidence, and every other calculator in the package reports it between zero
        // and one. This used to hand out the raw deviation, so a position a long way under water
        // reported a confidence of minus eight, and anything averaging strengths took that literally.
        return new Upside(clamped, Math.abs(clamped));
    }

    private Optional<Double> getSuitableLastExtremePrice(List<Candle> extremes, Instant fromTime) {
        if (extremes.isEmpty() || extremes.getLast().getTime().isBefore(fromTime)) {
            return Optional.empty();
        }

        return Optional.of(priceExtractor.extract(extremes.getLast()).toDouble());
    }

    /**
     * How far the price has moved from its reference, counted in volatilities.
     * <p>
     * Both halves are in the price's own units, which they were not: the deviation used to be divided
     * by the reference price and the volatility not at all, so a relative move was being divided by an
     * absolute one. The answer then carried the units of one over a price and shrank with the size of
     * the price. On a share around 1187 with an hourly ATR of 10, a move of two percent against the
     * position came out as 0.002 where it should be 2.3, and the signal reached one only if the price
     * moved by a thousand percent. On a share around 50 the same move came out twenty times larger,
     * for no reason but the price tag. The calculator was silent on everything.
     */
    private double calculateNormalizedDeviation(double currentPrice, double referencePrice, double volatility) {
        return (currentPrice - referencePrice) / (volatility == 0 ? Double.MIN_VALUE : volatility);
    }
}
