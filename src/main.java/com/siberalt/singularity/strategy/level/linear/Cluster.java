package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.shared.RangeDouble;

import java.util.Collections;
import java.util.Set;

public record Cluster(double price, Set<Candle> extremes, RangeDouble priceRange) {
    public Cluster(double price, Set<Candle> extremes, RangeDouble priceRange) {
        this.price = price;
        this.extremes = Collections.unmodifiableSet(extremes);
        this.priceRange = priceRange;
    }

    public int size() {
        return extremes.size();
    }
}