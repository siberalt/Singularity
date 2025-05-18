package com.siberalt.singularity.scheduler.simulation;

import com.siberalt.singularity.scheduler.ExecutionIteratorFactory;
import com.siberalt.singularity.scheduler.Schedule;
import com.siberalt.singularity.scheduler.testutil.SequenceTaskTester;
import com.siberalt.singularity.scheduler.testutil.StopTaskTester;
import com.siberalt.singularity.scheduler.testutil.TestExecution;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.EventSimulator;
import com.siberalt.singularity.simulation.SimulationClock;
import com.siberalt.singularity.simulation.time.SimpleSimulationClock;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.siberalt.singularity.scheduler.testutil.TestExecution.createIntervalDelayed;
import static com.siberalt.singularity.scheduler.testutil.TestExecution.createIntervalFixed;

public class SimulationSchedulerTest {
    protected EventSimulatedScheduler<String> scheduler;
    protected SimulationClock clock;
    protected EventObserver eventObserver;
    protected EventSimulator simulator;

    @BeforeEach
    protected void setUp() {
        clock = new SimpleSimulationClock();
        eventObserver = new EventObserver();
        scheduler = new EventSimulatedScheduler<>();
        scheduler.applyClock(clock);

        simulator = new EventSimulator(eventObserver, clock);
        simulator.addTimeDependentUnit(scheduler);
        simulator.addSynchronizableUnit(scheduler);
        scheduler.observeEventsBy(eventObserver);

        Logger logger = Logger.getLogger(EventSimulatedScheduler.class.getName());
        logger.setLevel(Level.OFF);
    }

    @Test
    public void testSingleFixed() {
        System.out.println("Testing single task on run:");

        SequenceTaskTester tester = createTester("task1");
        List.of(
            createIntervalFixed(Instant.parse("1997-05-07T10:15:00.00Z"), Duration.ofMinutes(30), 2),
            createIntervalFixed(Duration.ofDays(3), 5),
            createIntervalFixed(Duration.ofSeconds(25), 10),
            createIntervalFixed(Duration.ofMinutes(30), 5)
        ).forEach(tester::add);

        simulator.addTask(tester);
        simulator.run(
            Instant.parse("1997-05-01T10:15:00.00Z"),
            Instant.parse("1998-05-10T10:15:00.00Z")
        );

        Assertions.assertTrue(tester.isFinished(), "Task should be finished");
    }

    @Test
    public void testMultipleFixed() {
        System.out.println("Testing multiple tasks on run:");

        com.siberalt.singularity.scheduler.testutil.SequenceTaskTester[] testers = {
            createTester("task1", List.of(
                createIntervalFixed(Instant.parse("1997-05-07T00:00:00.00Z"), Duration.ofSeconds(10), 2),
                createIntervalFixed(Duration.ofHours(2), 5),
                createIntervalFixed(Duration.ofMillis(10), 5),
                createIntervalFixed(Duration.ofMinutes(20), 1)
            )),
            createTester("task2", List.of(
                createIntervalFixed(Instant.parse("1997-05-08T10:00:00.00Z"), Duration.ofSeconds(10), 1),
                createIntervalFixed(Duration.ofMillis(150), 1),
                createIntervalFixed(Duration.ofSeconds(60), 1),
                createIntervalFixed(Duration.ofDays(10), 1)
            )),
            createTester("task3", List.of(
                createIntervalFixed(Instant.parse("1997-05-08T10:00:00.00Z"), Duration.ofSeconds(10), 2),
                createIntervalFixed(Duration.ofMillis(150), 1),
                createIntervalFixed(Duration.ofSeconds(180), 6),
                createIntervalFixed(Duration.ofDays(1), 1)
            )),
            createTester("task4", List.of(
                createIntervalFixed(Instant.parse("1997-05-06T13:15:00.00Z"), Duration.ofMinutes(3), 5),
                createIntervalFixed(Duration.ofMillis(300), 1),
                createIntervalFixed(Duration.ofHours(12), 1),
                createIntervalFixed(Duration.ofSeconds(10), 4)
            ))
        };

        Arrays.stream(testers).forEach(simulator::addTask);
        simulator.run(
            Instant.parse("1997-05-01T10:15:00.00Z"),
            Instant.parse("1998-05-10T10:15:00.00Z")
        );
    }

    @Test
    public void testSingleDelayed_EffectiveExecution() {
        System.out.println("Testing single task on run:");
        Instant firstCallTime = Instant.parse("1997-05-07T10:15:00.00Z");

        SequenceTaskTester tester = createTester("task1",
            List.of(
                createIntervalDelayed(firstCallTime, Duration.ofSeconds(30), 10, Duration.ofSeconds(15))
            )
        );

        simulator.addTask(tester);
        simulator.run(
            Instant.parse("1997-05-01T10:15:00.00Z"),
            Instant.parse("1997-05-08T10:15:00.00Z")
        );

    }

    @Test
    public void testSingleDelayed_ExpiredExecution() {
        System.out.println("Testing single task on run:");
        Instant firstCallTime = Instant.parse("1997-05-07T10:15:00.00Z");

        SequenceTaskTester tester = createTester("task1",
            List.of(
                createIntervalDelayed(firstCallTime, Duration.ofSeconds(30), 10, Duration.ofSeconds(35))
            )
        );

        simulator.addTask(tester);
        simulator.run(
            Instant.parse("1997-05-01T10:15:00.00Z"),
            Instant.parse("1998-05-10T10:15:00.00Z")
        );
    }

    @Test
    public void testMultipleDelayed() {
        System.out.println("Testing multiple tasks on run:");

        com.siberalt.singularity.scheduler.testutil.SequenceTaskTester[] testers = {
            createTester("task1", List.of(
                createIntervalDelayed(Instant.parse("1997-05-07T00:00:00.00Z"), Duration.ofSeconds(10), 2, Duration.ofSeconds(15)),
                createIntervalDelayed(Duration.ofHours(2), 5, Duration.ofSeconds(5)),
                createIntervalDelayed(Duration.ofMillis(10), 5, Duration.ofSeconds(5)),
                createIntervalDelayed(Duration.ofMinutes(20), 1, Duration.ofSeconds(15))
            )),
            createTester("task2", List.of(
                createIntervalDelayed(Instant.parse("1997-05-08T10:00:00.00Z"), Duration.ofSeconds(10), 1, Duration.ofSeconds(15)),
                createIntervalDelayed(Duration.ofMillis(150), 1, Duration.ofMillis(160)),
                createIntervalDelayed(Duration.ofSeconds(60), 1, Duration.ofSeconds(10)),
                createIntervalDelayed(Duration.ofDays(10), 1, Duration.ofHours(1))
            )),
            createTester("task3", List.of(
                createIntervalDelayed(Instant.parse("1997-05-08T10:00:00.00Z"), Duration.ofSeconds(10), 2, Duration.ofSeconds(20)),
                createIntervalDelayed(Duration.ofMillis(150), 1, Duration.ofMillis(100)),
                createIntervalDelayed(Duration.ofSeconds(180), 6, Duration.ofSeconds(210)),
                createIntervalDelayed(Duration.ofDays(1), 1, Duration.ofHours(2))
            )),
            createTester("task4", List.of(
                createIntervalDelayed(Instant.parse("1997-05-06T13:15:00.00Z"), Duration.ofMinutes(3), 5, Duration.ofMinutes(4)),
                createIntervalDelayed(Duration.ofMillis(300), 1, Duration.ofMillis(200)),
                createIntervalDelayed(Duration.ofHours(12), 1, Duration.ofHours(16)),
                createIntervalDelayed(Duration.ofSeconds(10), 4, Duration.ofSeconds(5))
            ))
        };

        Arrays.stream(testers).forEach(simulator::addTask);
        simulator.run(
            Instant.parse("1997-05-01T10:15:00.00Z"),
            Instant.parse("1998-05-10T10:15:00.00Z")
        );
    }

    @Test
    public void testMultipleMixed() {
        System.out.println("Testing multiple tasks on run:");

        com.siberalt.singularity.scheduler.testutil.SequenceTaskTester[] testers = {
            createTester("task1", List.of(
                createIntervalFixed(Instant.parse("1997-05-07T00:00:00.00Z"), Duration.ofSeconds(10), 2),
                createIntervalDelayed(Duration.ofHours(2), 5, Duration.ofSeconds(5)),
                createIntervalDelayed(Duration.ofMillis(10), 5, Duration.ofSeconds(5)),
                createIntervalFixed(Duration.ofMinutes(20), 1)
            )),
            createTester("task2", List.of(
                createIntervalDelayed(Instant.parse("1997-05-08T10:00:00.00Z"), Duration.ofSeconds(10), 1, Duration.ofSeconds(15)),
                createIntervalDelayed(Duration.ofMillis(150), 1, Duration.ofMillis(160)),
                createIntervalFixed(Duration.ofSeconds(60), 1),
                createIntervalDelayed(Duration.ofDays(10), 1, Duration.ofHours(1))
            )),
            createTester("task3", List.of(
                createIntervalDelayed(Instant.parse("1997-05-08T10:00:00.00Z"), Duration.ofSeconds(10), 2, Duration.ofSeconds(20)),
                createIntervalDelayed(Duration.ofMillis(150), 1, Duration.ofMillis(100)),
                createIntervalDelayed(Duration.ofSeconds(180), 6, Duration.ofSeconds(210)),
                createIntervalFixed(Duration.ofDays(1), 1)
            )),
            createTester("task4", List.of(
                createIntervalFixed(Instant.parse("1997-05-06T13:15:00.00Z"), Duration.ofMinutes(3), 5),
                createIntervalFixed(Duration.ofMillis(300), 1),
                createIntervalDelayed(Duration.ofHours(12), 1, Duration.ofHours(16)),
                createIntervalFixed(Duration.ofSeconds(10), 4)
            ))
        };

        Arrays.stream(testers).forEach(simulator::addTask);
        simulator.run(
            Instant.parse("1997-05-01T10:15:00.00Z"),
            Instant.parse("1998-05-10T10:15:00.00Z")
        );
    }

    @Test
    public void testStop() {
        System.out.println("Testing stop schedule:");

        StopTaskTester tester = new StopTaskTester(scheduler, new Schedule<>(
            "task1",
            ExecutionIteratorFactory.createIntervalFixed(
                Instant.parse("1997-05-07T10:15:00.00Z"),
                Duration.ofSeconds(30),
                10
            )
        ), clock);

        simulator.addTask(() -> {
            try {
                tester.test(5);
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        simulator.run(
            Instant.parse("1997-05-01T10:15:00.00Z"),
            Instant.parse("1998-05-10T10:15:00.00Z")
        );
    }

    private SequenceTaskTester createTester(String taskId, List<TestExecution> testExecutions) {
        SequenceTaskTester tester = new com.siberalt.singularity.scheduler.testutil.SequenceTaskTester(taskId, scheduler, clock);
        testExecutions.forEach(tester::add);
        return tester;
    }

    // Helper methods
    private SequenceTaskTester createTester(String taskId) {
        return new SequenceTaskTester(taskId, scheduler, clock);
    }
}
