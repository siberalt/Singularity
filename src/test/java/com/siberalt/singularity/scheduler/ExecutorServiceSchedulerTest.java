package com.siberalt.singularity.scheduler;

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
    void testSingleFixed() {
        SequenceTaskTester tester = createTester(
            "testSingleFixed",
            List.of(
                TestExecution.createIntervalFixed(
                    clock.currentTime().plus(Duration.ofSeconds(1)),
                    Duration.ofSeconds(1),
                    5
                )
            )
        );

        tester.test();

        // Wait for the test to finish
        while (!tester.isFinished()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void testMultiFixed() {
        System.out.println("Testing multiple tasks on run:");
        Instant currentTime = clock.currentTime().plus(Duration.ofSeconds(1));

        SequenceTaskTester[] testers = {
            createTester("task1", List.of(
                createIntervalFixed(currentTime, Duration.ofSeconds(10), 2),
                createIntervalFixed(Duration.ofMillis(50), 5)
            )),
            createTester("task2", List.of(
                createIntervalFixed(currentTime, Duration.ofSeconds(10), 3),
                createIntervalFixed(Duration.ofMillis(150), 1)
            )),
        };

        for (SequenceTaskTester tester : testers) {
            tester.test();
        }

        // Wait for the all tests to finish
        while (!testers[0].isFinished() || !testers[1].isFinished()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void testStop() {
        StopTaskTester tester = new StopTaskTester(scheduler, new Schedule<>(
            "testStop",
            ExecutionIteratorFactory.createIntervalFixed(
                Instant.now().plus(Duration.ofSeconds(1)),
                Duration.ofSeconds(30),
                10
            )
        ));

        tester.test(5);
    }

    private SequenceTaskTester createTester(String taskId, List<TestExecution> testExecutions) {
        SequenceTaskTester tester = new SequenceTaskTester(taskId, scheduler, clock);
        testExecutions.forEach(tester::add);
        return tester;
    }
}
