package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Какую позицию держать: доля счёта со знаком, в [-1, 1]. 1 - весь счёт в лонге, -1 - в шорте, 0 - в
 * деньгах.
 * <p>
 * Это решение, а не мера рынка, поэтому не {@link com.siberalt.singularity.strategy.market.MarketCoefficient}:
 * цель может зависеть от того, что правило решило раньше, а мера рынка - только от рынка. Сигналом её
 * делает {@link TargetSignalSource}.
 */
@FunctionalInterface
public interface PositionTarget {
    double of(List<Candle> lastCandles);
}
