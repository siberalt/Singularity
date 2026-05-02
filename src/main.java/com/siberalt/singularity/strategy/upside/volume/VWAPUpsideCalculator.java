package com.siberalt.singularity.strategy.upside.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;
import java.util.function.Function;

/**
 * Улучшенный VWAPCalculator, возвращающий нормализованный сигнал в диапазоне [-1, 1]
 * на основе статистического отклонения от VWAP (в сигмах).
 */
public class VWAPUpsideCalculator implements UpsideCalculator {
    private Function<Candle, Double> priceExtractor = Candle::getClosePriceAsDouble;

    public VWAPUpsideCalculator(Function<Candle, Double> priceExtractor) {
        this.priceExtractor = priceExtractor;
    }

    public VWAPUpsideCalculator() {
    }

    @Override
    public Upside calculate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        // 1. Рассчитываем VWAP
        double totalValue = candles.stream().mapToDouble(t -> priceExtractor.apply(t) * t.getVolume()).sum();
        double totalVolume = candles.stream().mapToDouble(Candle::getVolume).sum();

        if (totalVolume == 0) {
            return Upside.NEUTRAL;
        }

        double vwap = totalValue / totalVolume;
        double lastPrice = priceExtractor.apply(candles.get(candles.size() - 1));
        double priceDeviation = (lastPrice - vwap) / vwap; // относительное отклонение

        // 2. Оценка волатильности (простая — среднее |отклонение| за последние N периодов)
        // Предположим, что `candles` сгруппированы по барам (например, 1 мин), и мы храним историю
        // Здесь упрощённый подход: используем только текущий бар, но можно расширить

        // Пример: если у вас есть доступ к истории баров, собирайте исторические отклонения
        // Пока используем фиксированную шкалу: типичное отклонение ~0.1% = 0.001
        double typicalDeviation = 0.001; // 0.1% — характерно для 1-минутного бара

        // 3. Нормализуем отклонение в "сигмы"
        double normalizedSignal = priceDeviation / typicalDeviation;

        // 4. Сжимаем через tanh для ограничения в [-1, 1]
        return new Upside(Math.tanh(normalizedSignal), Math.abs(priceDeviation));
    }
}