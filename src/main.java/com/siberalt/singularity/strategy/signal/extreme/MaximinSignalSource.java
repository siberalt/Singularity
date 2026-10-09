package com.siberalt.singularity.strategy.signal.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;

import java.util.List;
import java.util.function.Function;

/**
 * Калькулятор signal, основанный на положении текущей цены относительно локальных максимумов и минимумов.
 * <p>
 * Использует {@link ExtremeLocator} для поиска ключевых экстремумов и возвращает:
 * <ul>
 *   <li><b>+1.0</b> — если цена находится у самого низкого минимума</li>
 *   <li><b>-1.0</b> — если цена находится у самого высокого максимума</li>
 *   <li><b>0.0</b> — если нет выраженных экстремумов или цена в середине диапазона</li>
 * </ul>
 * </p>
 */
public class MaximinSignalSource implements SignalSource {

    private final ExtremeLocator maxExtremeLocator;
    private final ExtremeLocator minExtremeLocator;
    private Function<Candle, Double> priceExtractor = Candle::getCloseAsDouble;

    public MaximinSignalSource(ExtremeLocator maxExtremeLocator, ExtremeLocator minExtremeLocator) {
        this.maxExtremeLocator = maxExtremeLocator;
        this.minExtremeLocator = minExtremeLocator;
    }

    public MaximinSignalSource(
        ExtremeLocator maxExtremeLocator,
        ExtremeLocator minExtremeLocator,
        Function<Candle, Double> priceExtractor
    ) {
        this.maxExtremeLocator = maxExtremeLocator;
        this.minExtremeLocator = minExtremeLocator;
        this.priceExtractor = priceExtractor;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Signal.NEUTRAL;
        }

        List<Candle> maximums = maxExtremeLocator.locate(lastCandles);
        if (maximums == null || maximums.isEmpty()) {
            return Signal.NEUTRAL;
        }

        List<Candle> minimums = minExtremeLocator.locate(lastCandles);
        if (minimums == null || minimums.isEmpty()) {
            return Signal.NEUTRAL;
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
            return Signal.NEUTRAL;
        }

        // Нормализуем позицию в диапазоне [minExtreme, maxExtreme]
        double normalizedPosition = (currentPrice - minExtreme) / (maxExtreme - minExtreme);

        // Инвертируем: чем ближе к минимуму — тем выше signal
        double rawSignal = 1.0 - 2.0 * normalizedPosition; // [0→1] → [+1→-1]

        // Принудительно ограничиваем [-1, +1]
        double clampedSignal = Double.compare(rawSignal, 1.0) >= 0 ? 1.0 :
            Double.compare(rawSignal, -1.0) <= 0 ? -1.0 : rawSignal;

        return new Signal(clampedSignal, rawSignal);
    }
}
