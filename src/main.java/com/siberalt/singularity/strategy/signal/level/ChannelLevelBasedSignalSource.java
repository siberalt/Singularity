package com.siberalt.singularity.strategy.signal.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.signal.Signal;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Калькулятор потенциала роста (signal), основанный на ценовом канале,
 * определённом уровнями поддержки и сопротивления.
 *
 * <p>Рассчитывает нормализованную метрику, отражающую положение текущей цены
 * внутри канала с учётом силы уровней.</p>
 *
 * <p><b>Спецификация выходного значения:</b><br>
 * - {@code +1.0} — 100% позитивный сигнал для long-позиции (максимальный signal)<br>
 * - {@code -1.0} — 100% негативный сигнал (минимальный signal / высокий риск)</p>
 */
public class ChannelLevelBasedSignalSource implements LevelBasedSignalSource {

    private static final Logger logger = Logger.getLogger(ChannelLevelBasedSignalSource.class.getName());

    private PriceExtractor priceExtractor = Candle::close;

    public ChannelLevelBasedSignalSource(PriceExtractor priceExtractor) {
        this.priceExtractor = priceExtractor;
    }

    public ChannelLevelBasedSignalSource() {
    }

    @Override
    public Signal calculate(LevelPair levelPair, List<Candle> recentCandles) {
        var resistance = levelPair.resistance();
        var support   = levelPair.support();

        // Проверки на null
        Objects.requireNonNull(resistance, "Resistance level must not be null");
        Objects.requireNonNull(support, "Support level must not be null");
        Objects.requireNonNull(recentCandles, "Recent candles list must not be null");
        if (recentCandles.isEmpty()) {
            throw new IllegalArgumentException("Recent candles list must not be empty");
        }

        Candle lastCandle = recentCandles.get(recentCandles.size() - 1);
        long currentIndex = lastCandle.getIndex();
        double currentPrice = priceExtractor.extract(lastCandle).toDouble();

        double resistancePrice = resistance.function().apply((double) currentIndex);
        double supportPrice = support.function().apply((double) currentIndex);
        double resistanceStrength = resistance.strength();
        double supportStrength = support.strength();

        // Логирование при выходе за границы канала
        if (currentPrice > resistancePrice || currentPrice < supportPrice) {
            logger.warning(() -> String.format(
                "Current price %.5f is out of channel bounds [%.5f, %.5f] at index %d. Returning NEUTRAL signal.",
                currentPrice, supportPrice, resistancePrice, currentIndex
            ));
            return Signal.NEUTRAL;
        }

        // Защита от деления на ноль суммы сил
        if (resistanceStrength + supportStrength == 0) {
            logger.warning("Sum of resistance and support strengths is zero. Using equal weights.");
            resistanceStrength = supportStrength = 1.0;
        }

        double channelWidth = resistancePrice - supportPrice;
        if (channelWidth <= 0) {
            logger.warning(() -> String.format(
                "Invalid channel width: %.5f (resistance=%.5f, support=%.5f). Returning NEUTRAL.",
                channelWidth, resistancePrice, supportPrice
            ));
            return Signal.NEUTRAL;
        }

        // Нормализованная позиция в канале: [0, 1]
        double normalizedPosition = (currentPrice - supportPrice) / channelWidth;
        double baseSignal = 1 - 2 * normalizedPosition; // [-1, +1]
        double totalStrength = resistanceStrength + supportStrength;
        double bias = (supportStrength - resistanceStrength) / Math.max(totalStrength, 1e-8); // [-1, +1]
        double adjustment = bias * (1 - Math.abs(baseSignal)); // [-1, +1], но масштабировано
        double rawSignal = baseSignal + adjustment;

        // Гарантируется: rawSignal ∈ [-1, +1] → clamp не нужен
        return new Signal(rawSignal, rawSignal);
    }
}
