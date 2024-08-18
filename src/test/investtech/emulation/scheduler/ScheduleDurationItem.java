package investtech.emulation.scheduler;

import investtech.strategy.scheduler.Schedule;

import java.time.Duration;

public class ScheduleDurationItem {
    protected Duration interval;

    protected Schedule.ExecutionType executionType;

    public Duration getInterval() {
        return interval;
    }

    public ScheduleDurationItem setInterval(Duration interval) {
        this.interval = interval;
        return this;
    }

    public Schedule.ExecutionType getExecutionType() {
        return executionType;
    }

    public ScheduleDurationItem setExecutionType(Schedule.ExecutionType executionType) {
        this.executionType = executionType;
        return this;
    }

    public static ScheduleDurationItem of(Duration interval, Schedule.ExecutionType executionType) {
        return new ScheduleDurationItem().setExecutionType(executionType).setInterval(interval);
    }
}
