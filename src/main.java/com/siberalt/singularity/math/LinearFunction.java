package com.siberalt.singularity.math;

import java.util.function.Function;

public interface LinearFunction<T extends Number> extends Function<T, T> {
    T getSlope();

    T getIntercept();
}
