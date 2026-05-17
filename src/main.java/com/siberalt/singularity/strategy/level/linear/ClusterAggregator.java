package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public interface ClusterAggregator {
    List<Cluster> aggregate(List<Candle> extremes, double volatility);
}
