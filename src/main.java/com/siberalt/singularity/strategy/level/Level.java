package com.siberalt.singularity.strategy.level;

import java.time.Instant;
import java.util.function.Function;

public interface Level<T> {
    Instant getTimeFrom();
    Instant getTimeTo();
    long getIndexFrom();
    long getIndexTo();
    Function<T, T> getFunction();
    double getStrength();
}
