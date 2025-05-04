package com.siberalt.singularity.scheduler;

import com.siberalt.singularity.strategy.context.Clock;

import java.time.Duration;
import java.time.Instant;

public class IntervalIterator implements ExecutionIterator {
    private final Duration interval;
    private Duration delay;
    private final ExecutionType executionType;
    private int executionCount = 0;
    private int maxExecutionCount = Integer.MAX_VALUE;
    private Instant startTime;

    public IntervalIterator(Instant startTime, Duration interval, ExecutionType executionType, int maxExecutionCount) {
        this.interval = interval;
        this.executionType = executionType;
        this.startTime = startTime;
        this.maxExecutionCount = maxExecutionCount;
    }

    public IntervalIterator(Instant startTime, Duration interval, ExecutionType executionType) {
        this.interval = interval;
        this.executionType = executionType;
        this.startTime = startTime;
    }

    public IntervalIterator(Duration interval, ExecutionType executionType, int maxExecutionCount) {
        this.interval = interval;
        this.executionType = executionType;
        this.maxExecutionCount = maxExecutionCount;
    }

    public IntervalIterator(Duration interval, ExecutionType executionType) {
        this.interval = interval;
        this.executionType = executionType;
    }

    public IntervalIterator(Duration interval, Duration delay, ExecutionType executionType) {
        this.interval = interval;
        this.delay = delay;
        this.executionType = executionType;
    }

    @Override
    public String toString() {
        return "IntervalIterator{" +
            "interval=" + interval +
            ", delay=" + delay +
            ", executionType=" + executionType +
            ", maxExecutionCount=" + maxExecutionCount +
            ", startTime=" + startTime +
            '}';
    }

    @Override
    public Execution getNext(Clock clock) {
        if (executionCount >= maxExecutionCount) {
            return null; // No more executions allowed
        }

        if (startTime != null) {
            delay = Duration.between(clock.currentTime(), startTime);
        }

        if (delay != null && delay.isNegative()) {
            delay = Duration.ZERO;
        }

        Duration period = executionCount++ == 0 && null != delay ? delay : interval;

        return new Execution(period, executionType);
    }
}
