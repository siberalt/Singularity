package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Вес каждой точки уровня в этом окне. Считается один раз на окно, при вызове {@link #weigh}, и вызывать
 * возвращённую функцию можно для любой из переданных точек. Списки взвешиватель после вызова не читает:
 * детектор вправе их менять, и веса от этого не меняются. Веса неотрицательны; равные веса - прежнее
 * поведение, при котором уровни соревнуются числом точек.
 */
@FunctionalInterface
public interface ExtremeWeigher {
    ExtremeWeigher EQUAL = (extremes, window) -> extreme -> 1.0;

    ToDoubleFunction<Candle> weigh(List<Candle> extremes, List<Candle> window);
}
