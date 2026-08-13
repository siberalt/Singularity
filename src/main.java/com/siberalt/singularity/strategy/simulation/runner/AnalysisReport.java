package com.siberalt.singularity.strategy.simulation.runner;

public record AnalysisReport(
    StrategyResult mainStrategyResult,
    StrategyResult conservativeStrategyResult,
    double effectivenessRatio,
    String accountId
) {
}
