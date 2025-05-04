package com.siberalt.singularity.scheduler;

import java.time.Duration;

public record Execution(Duration period, ExecutionType executionType) {}
