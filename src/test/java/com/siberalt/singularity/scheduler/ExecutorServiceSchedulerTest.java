package com.siberalt.singularity.scheduler;

import com.siberalt.singularity.scheduler.executor.ExecutorServiceScheduler;
import com.siberalt.singularity.scheduler.testutil.SequenceTaskTester;
import com.siberalt.singularity.scheduler.testutil.StopTaskTester;
import com.siberalt.singularity.scheduler.testutil.TestExecution;
import com.siberalt.singularity.strategy.context.Clock;
import com.siberalt.singularity.strategy.context.execution.time.RealTimeClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static com.siberalt.singularity.scheduler.testutil.TestExecution.createIntervalFixed;

public class ExecutorServiceSchedulerTest {
    private ExecutorServiceScheduler<String> scheduler = new ExecutorServiceScheduler<>();
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = new RealTimeClock();
        scheduler = new ExecutorServiceScheduler<>(clock);
    }

    @Test
    void testSingleFixed() throws ExecutionException, InterruptedException {
        SequenceTaskTester tester = createTester(
            "testSingleFixed",
            List.of(
                TestExecution.createIntervalFixed(
                    clock.currentTime(),
                    Duration.ofSeconds(1),
                    3
                )
            )
        );

        tester.test();
    }

    @Test
    void testMultiFixed() throws ExecutionException, InterruptedException {
        System.out.println("Testing multiple tasks on run:");
        Instant currentTime = clock.currentTime().plus(Duration.ofSeconds(1));

        SequenceTaskTester[] testers = {
            createTester("task1", List.of(
                createIntervalFixed(currentTime, Duration.ofSeconds(5), 2),
                createIntervalFixed(Duration.ofMillis(50), 5)
            )),
            createTester("task2", List.of(
                createIntervalFixed(currentTime, Duration.ofSeconds(3), 3),
                createIntervalFixed(Duration.ofMillis(150), 1)
            )),
        };

        for (SequenceTaskTester tester : testers) {
            tester.test();
        }
    }

    @Test
    void testStop() throws ExecutionException, InterruptedException {
        StopTaskTester tester = new StopTaskTester(scheduler, new Schedule<>(
            "testStop",
            ExecutionIteratorFactory.createIntervalFixed(
                Instant.now(),
                Duration.ofSeconds(2),
                10
            )
        ),
            clock
        );

        tester.test(5);
    }

    private SequenceTaskTester createTester(String taskId, List<TestExecution> testExecutions) {
        SequenceTaskTester tester = new SequenceTaskTester(taskId, scheduler, clock);
        testExecutions.forEach(tester::add);
        return tester;
    }
}
