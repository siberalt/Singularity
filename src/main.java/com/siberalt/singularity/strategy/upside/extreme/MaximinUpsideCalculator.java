package com.siberalt.singularity.strategy.upside.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;
import java.util.function.Function;

/**
 * Калькулятор upside, основанный на положении текущей цены относительно локальных максимумов и минимумов.
 * <p>
 * Использует {@link ExtremeLocator} для поиска ключевых экстремумов и возвращает:
 * <ul>
 *   <li><b>+1.0</b> — если цена находится у самого низкого минимума</li>
 *   <li><b>-1.0</b> — если цена находится у самого высокого максимума</li>
 *   <li><b>0.0</b> — если нет выраженных экстремумов или цена в середине диапазона</li>
 * </ul>
 * </p>
 */
public class MaximinUpsideCalculator implements UpsideCalculator {

    private final ExtremeLocator maxExtremeLocator;
    private final ExtremeLocator minExtremeLocator;
    private Function<Candle, Double> priceExtractor = Candle::getTypicalAsDouble;

    public MaximinUpsideCalculator(ExtremeLocator maxExtremeLocator, ExtremeLocator minExtremeLocator) {
        this.maxExtremeLocator = maxExtremeLocator;
        this.minExtremeLocator = minExtremeLocator;
    }

    public MaximinUpsideCalculator(
        ExtremeLocator maxExtremeLocator,
        ExtremeLocator minExtremeLocator,
        Function<Candle, Double> priceExtractor
    ) {
        this.maxExtremeLocator = maxExtremeLocator;
        this.minExtremeLocator = minExtremeLocator;
        this.priceExtractor = priceExtractor;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        List<Candle> maximums = maxExtremeLocator.locate(lastCandles);
        if (maximums == null || maximums.isEmpty()) {
            return Upside.NEUTRAL;
        }

        List<Candle> minimums = minExtremeLocator.locate(lastCandles);
        if (minimums == null || minimums.isEmpty()) {
            return Upside.NEUTRAL;
        }

        Candle lastCandle = lastCandles.get(lastCandles.size() - 1);
        double currentPrice = priceExtractor.apply(lastCandle);

        double maxExtreme = maximums.stream()
            .mapToDouble(candle -> priceExtractor.apply(candle))
            .max()
            .orElse(Double.NaN);

        double minExtreme = minimums.stream()
            .mapToDouble(candle -> priceExtractor.apply(candle))
            .min()
            .orElse(Double.NaN);

        if (Double.isNaN(minExtreme) || Double.isNaN(maxExtreme) || maxExtreme <= minExtreme) {
            return Upside.NEUTRAL;
        }

        // Нормализуем позицию в диапазоне [minExtreme, maxExtreme]
        double normalizedPosition = (currentPrice - minExtreme) / (maxExtreme - minExtreme);

        // Инвертируем: чем ближе к минимуму — тем выше upside
        double rawUpside = 1.0 - 2.0 * normalizedPosition; // [0→1] → [+1→-1]

        // Принудительно ограничиваем [-1, +1]
        double clampedUpside = Double.compare(rawUpside, 1.0) >= 0 ? 1.0 :
            Double.compare(rawUpside, -1.0) <= 0 ? -1.0 : rawUpside;

        return new Upside(clampedUpside, rawUpside);
    }
}
