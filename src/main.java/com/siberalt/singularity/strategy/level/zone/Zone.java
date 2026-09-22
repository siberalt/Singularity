package com.siberalt.singularity.strategy.level.zone;

import com.siberalt.singularity.entity.candle.TimePoint;

/**
 * Горизонтальная зона: полоса цен, у которой рынок разворачивался не один раз.
 * <p>
 * В отличие от {@link com.siberalt.singularity.strategy.level.Level} у зоны нет наклона, зато есть
 * ширина.
 *
 * @param pointFrom     первый экстремум зоны
 * @param pointTo       последний экстремум зоны
 * @param low           нижняя граница
 * @param high          верхняя граница
 * @param price         средневзвешенная цена её экстремумов - где зона плотнее всего
 * @param strength      суммарный вес её экстремумов, в весах средней точки окна
 * @param touchesCount  сколько экстремумов её образовали
 */
public record Zone(
    TimePoint pointFrom,
    TimePoint pointTo,
    double low,
    double high,
    double price,
    double strength,
    int touchesCount
) {
    public Zone {
        if (low > high) {
            throw new IllegalArgumentException("Нижняя граница зоны выше верхней: " + low + " > " + high);
        }
    }

    public double width() {
        return high - low;
    }

    public boolean contains(double price) {
        return low <= price && price <= high;
    }
}
