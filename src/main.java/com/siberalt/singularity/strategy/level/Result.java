package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.math.LinearFunction2D;

import java.time.Instant;
import java.util.List;

public record Result<T extends Number>(
    String instrumentId,
    List<Level<T>> levels

) {
    public record Level<T extends Number>(
        Instant from,
        Instant to,
        long indexFrom,
        long indexTo,
        LinearFunction2D<T> function,
        double strength
    ){

    }
}
