package com.siberalt.singularity.strategy.simulation.runner;

import com.siberalt.singularity.broker.contract.value.money.Money;

import java.time.Duration;

public record StrategyResult(
    Money profit,
    double profitPercent,
    Money balance,
    double apy,
    Duration executionDuration,
    String accountId
) {
}
