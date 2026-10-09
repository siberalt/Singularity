package com.siberalt.singularity.strategy.signal.volume;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.indicator.Vwap;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;

import java.util.List;
import java.util.function.Function;

/**
 * Калькулятор Signal, возвращающий нормализованный сигнал в диапазоне [-1, 1]
 * на основе отклонения последней цены от VWAP.
 * <p>
 * Сигнал = tanh( (цена - VWAP) / VWAP / typicalDeviation ),
 * сила (strength) = abs(цена - VWAP) / VWAP.
 * </p>
 * <p>
 * VWAP считается в {@link Vwap}, один расчёт для всего проекта.
 * Если свечей нет или объём нулевой, возвращается Signal.NEUTRAL.
 * </p>
 */
public class VWAPSignalSource implements SignalSource {
    /** Типичное относительное отклонение цены от VWAP: 0.1% - характерно для 1-минутного бара. */
    public static final double DEFAULT_TYPICAL_DEVIATION = 0.001;

    private final Function<Candle, Double> priceExtractor;
    private final double typicalDeviation;

    /**
     * Конструктор с полной настройкой.
     *
     * @param priceExtractor   какую цену брать у свечи
     * @param typicalDeviation относительное отклонение от VWAP, которому соответствует одна "сигма" (> 0)
     */
    public VWAPSignalSource(Function<Candle, Double> priceExtractor, double typicalDeviation) {
        if (!(typicalDeviation > 0)) {
            throw new IllegalArgumentException("The typical deviation must be positive, got " + typicalDeviation);
        }

        this.priceExtractor = priceExtractor;
        this.typicalDeviation = typicalDeviation;
    }

    public VWAPSignalSource(Function<Candle, Double> priceExtractor) {
        this(priceExtractor, DEFAULT_TYPICAL_DEVIATION);
    }

    public VWAPSignalSource(double typicalDeviation) {
        this(Candle::getCloseAsDouble, typicalDeviation);
    }

    public VWAPSignalSource() {
        this(Candle::getCloseAsDouble, DEFAULT_TYPICAL_DEVIATION);
    }

    @Override
    public Signal calculate(List<Candle> candles) {
        double vwap = Vwap.of(candles, priceExtractor);

        if (Double.isNaN(vwap)) {
            return Signal.NEUTRAL;
        }

        double lastPrice = priceExtractor.apply(candles.getLast());
        double priceDeviation = (lastPrice - vwap) / vwap; // относительное отклонение

        // Нормализуем отклонение в "сигмы" и сжимаем через tanh до [-1, 1]
        return new Signal(Math.tanh(priceDeviation / typicalDeviation), Math.abs(priceDeviation));
    }
}
