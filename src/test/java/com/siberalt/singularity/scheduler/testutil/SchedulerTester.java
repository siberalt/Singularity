package com.siberalt.singularity.scheduler.testutil;

import com.siberalt.singularity.scheduler.Scheduler;
import com.siberalt.singularity.strategy.context.Clock;
import com.siberalt.singularity.strategy.context.execution.time.RealTimeClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.siberalt.singularity.scheduler.testutil.TestExecution.createIntervalFixed;

public class SchedulerTester {
    private final Scheduler<String> scheduler;
    private final Clock clock;

    public SchedulerTester(Scheduler<String> scheduler, Clock clock) {
        this.scheduler = scheduler;
        this.clock = clock;
    }

    public SchedulerTester(Scheduler<String> scheduler) {
        this.scheduler = scheduler;
        this.clock = new RealTimeClock();
    }

    public void testSingleFixed() {
        SequenceTaskTester tester = createTester("task1");
        List.of(
            createIntervalFixed(Instant.parse("1997-05-07T10:15:00.00Z"), Duration.ofMinutes(30), 2),
            createIntervalFixed(Duration.ofDays(3), 5),
            createIntervalFixed(Duration.ofSeconds(25), 10),
            createIntervalFixed(Duration.ofMinutes(30), 5)
        ).forEach(tester::add);

        tester.test();
    }

    public void testStop() {
        SequenceTaskTester tester = createTester("task2");
        List.of(
            createIntervalFixed(Instant.parse("1997-05-07T10:15:00.00Z"), Duration.ofMinutes(30), 2),
            createIntervalFixed(Duration.ofDays(3), 5),
            createIntervalFixed(Duration.ofSeconds(25), 10),
            createIntervalFixed(Duration.ofMinutes(30), 5)
        ).forEach(tester::add);
    }

    private SequenceTaskTester createTester(String taskId, List<TestExecution> testExecutions) {
        SequenceTaskTester tester = new SequenceTaskTester(taskId, scheduler, clock);
        testExecutions.forEach(tester::add);
        return tester;
    }

    // Helper methods
    private SequenceTaskTester createTester(String taskId) {
        return new SequenceTaskTester(taskId, scheduler, clock);
    }

}
