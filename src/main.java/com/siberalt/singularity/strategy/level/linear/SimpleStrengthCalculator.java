package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Простой калькулятор силы уровня.
 * Сила = touchesCount * (ln(длительность периода) + 1)
 * Чем больше касаний и чем длиннее уровень существует, тем он сильнее.
 */
public class SimpleStrengthCalculator implements StrengthCalculator {
    private final double priceSensitivity;

    private final double timeDecay; // период полураспада в барах

    private Function<Candle, Double> priceExtractor = Candle::getCloseAsDouble;

    public SimpleStrengthCalculator(double priceSensitivity, double timeDecay) {
        this.priceSensitivity = priceSensitivity;
        this.timeDecay = timeDecay;
    }

    public SimpleStrengthCalculator() {
        this(10.0, 30.0);
    }

    public SimpleStrengthCalculator(double priceSensitivity, double timeDecay, Function<Candle, Double> priceExtractor) {
        this.priceSensitivity = priceSensitivity;
        this.timeDecay = timeDecay;
        this.priceExtractor = priceExtractor;
    }

    @Override
    public double calculate(Level<Double> level, List<Candle> candles) {
        Objects.requireNonNull(level);

        // 1. Базовая сила: касания * вес длительности периода (от pointFrom до pointTo)
        long duration = level.indexTo() - level.indexFrom() + 1;
        if (duration <= 0) return level.touchesCount();
        double durationWeight = Math.log(duration + 1);
        double baseStrength = level.touchesCount() * durationWeight;

        if (candles == null || candles.isEmpty()) {
            return baseStrength;
        }

        // Текущая цена и значение уровня в текущий момент
        Candle lastCandle = candles.get(candles.size() - 1);
        double currentPrice = priceExtractor.apply(lastCandle);
        long currentIndex = lastCandle.getIndex(); // предполагаем, что Candle имеет getIndex()

        // 2. Ценовая близость: значение уровня в текущий момент
        double levelValue = level.function().apply((double) currentIndex);
        double distance = Math.abs(levelValue - currentPrice) / currentPrice;
        double priceFactor = Math.exp(-distance * priceSensitivity);

        // 3. Временная близость: как давно было последнее касание (pointTo)
        long lastTouchIndex = level.indexTo();
        long barsSinceLastTouch = currentIndex - lastTouchIndex;
        double timeFactor = Math.exp(-barsSinceLastTouch / timeDecay);

        return baseStrength * priceFactor * timeFactor;
    }
}
