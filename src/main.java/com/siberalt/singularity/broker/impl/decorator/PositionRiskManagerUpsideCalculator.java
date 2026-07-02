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
 * Калькулятор Upside для риск-менеджмента на основе отклонения цены от средней цены позиции.
 * <p>
 * Проверяет отклонение текущей цены от средней цены открытой позиции (или последнего экстремума)
 * с учётом волатильности.
 * </p>
 * <p>
 * Для long-позиции:
 * - Если цена отклоняется на < multiplier * volatility от средней цены, возвращается сигнал -1
 * - Если цена отклоняется на < multiplier * volatility от последнего максимума (когда максимум > средней цены), возвращается -1
 * <p>
 * Для short-позиции (отрицательная позиция):
 * - Если цена отклоняется на < multiplier * volatility от средней цены, возвращается сигнал 1
 * - Если цена отклоняется на < multiplier * volatility от последнего минимума (когда минимум < средней цены), возвращается 1
 * </p>
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

        return new Upside(Math.min(1, Math.max(-1, signal)), signal);
    }

    private Optional<Double> getSuitableLastExtremePrice(List<Candle> extremes, Instant fromTime) {
        if (extremes.isEmpty() || extremes.getLast().getTime().isBefore(fromTime)) {
            return Optional.empty();
        }

        return Optional.of(priceExtractor.extract(extremes.getLast()).toDouble());
    }

    private double calculateNormalizedDeviation(double currentPrice, double referencePrice, double volatility) {
        double deviation = (currentPrice - referencePrice) / referencePrice;
        return deviation / (volatility == 0 ? Double.MIN_VALUE : volatility);
    }
}
