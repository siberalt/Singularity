package com.siberalt.singularity.simulation.scheduler;

import com.siberalt.singularity.scheduler.Schedule;
import com.siberalt.singularity.scheduler.SchedulerInterface;
import com.siberalt.singularity.scheduler.simulation.SimulationScheduler;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.EventSimulator;
import com.siberalt.singularity.strategy.context.TimeSynchronizerInterface;
import com.siberalt.singularity.strategy.context.simulation.time.SimulationTimeSynchronizer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class SimulationSchedulerTest {
    protected SimulationScheduler scheduler;
    protected SimulationTimeSynchronizer timeSynchronizer;
    protected EventObserver eventObserver;
    protected EventSimulator simulator;

    @BeforeEach
    protected void init() {
        timeSynchronizer = new SimulationTimeSynchronizer();
        eventObserver = new EventObserver();
        scheduler = new SimulationScheduler(timeSynchronizer);

        simulator = new EventSimulator(eventObserver, timeSynchronizer);
        simulator.addTimeDependentUnit(scheduler);

        scheduler.observeEventsBy(eventObserver);
    }

    @Test
    public void testSingle() {
        System.out.println("Testing single task on run:");

        var runnable = createDummyRunnable(
            "task1",
            List.of(
                Schedule.of(
                    Instant.parse("1997-05-07T10:15:00.00Z"),
                    Duration.ofMinutes(30),
                    Schedule.ExecutionType.DEFAULT
                ),
                Schedule.of(
                    Duration.ofDays(3),
                    Schedule.ExecutionType.FIXED_RATE
                ),
                Schedule.of(
                    Duration.ofSeconds(25),
                    Schedule.ExecutionType.FIXED_RATE
                ),
                Schedule.of(
                    Duration.ofMinutes(30),
                    Schedule.ExecutionType.FIXED_RATE
                )
            ),
            scheduler,
            timeSynchronizer
        );

        runnable.run();

        simulator.run(
            Instant.parse("1997-05-01T10:15:00.00Z"),
            Instant.parse("1998-05-10T10:15:00.00Z")
        );
    }

    @Test
    public void testMultiple() {
        System.out.println("Testing multiple tasks on run:");

        Runnable[] tasks = new Runnable[]{
            createDummyRunnable(
                "task1",
                List.of(
                    Schedule.of(
                        Instant.parse("1997-05-07T00:00:00.00Z"),
                        Duration.ofSeconds(10),
                        Schedule.ExecutionType.DEFAULT
                    ),
                    Schedule.of(
                        Duration.ofHours(2),
                        Schedule.ExecutionType.FIXED_RATE
                    ),
                    Schedule.of(
                        Duration.ofMillis(10),
                        Schedule.ExecutionType.FIXED_DELAYED
                    ),
                    Schedule.of(
                        Duration.ofMinutes(20),
                        Schedule.ExecutionType.DEFAULT
                    )
                ),
                scheduler,
                timeSynchronizer
            ),
            createDummyRunnable(
                "task2",
                List.of(
                    Schedule.of(
                        Instant.parse("1997-05-08T10:00:00.00Z"),
                        Duration.ofSeconds(10),
                        Schedule.ExecutionType.DEFAULT
                    ),
                    Schedule.of(
                        Duration.ofMillis(150),
                        Schedule.ExecutionType.FIXED_RATE
                    ),
                    Schedule.of(
                        Duration.ofSeconds(60),
                        Schedule.ExecutionType.FIXED_DELAYED
                    ),
                    Schedule.of(
                        Duration.ofDays(10),
                        Schedule.ExecutionType.DEFAULT
                    )
                ),
                scheduler,
                timeSynchronizer
            ),
            createDummyRunnable(
                "task3",
                List.of(
                    Schedule.of(
                        Instant.parse("1997-05-08T10:00:00.00Z"),
                        Duration.ofSeconds(10),
                        Schedule.ExecutionType.DEFAULT
                    ),
                    Schedule.of(
                        Duration.ofMillis(150),
                        Schedule.ExecutionType.FIXED_RATE
                    ),
                    Schedule.of(
                        Duration.ofSeconds(180),
                        Schedule.ExecutionType.FIXED_DELAYED
                    ),
                    Schedule.of(
                        Duration.ofDays(1),
                        Schedule.ExecutionType.DEFAULT
                    )
                ),
                scheduler,
                timeSynchronizer
            ),
            createDummyRunnable(
                "task4",
                List.of(
                    Schedule.of(
                        Instant.parse("1997-05-06T13:15:00.00Z"),
                        Duration.ofMinutes(3),
                        Schedule.ExecutionType.FIXED_RATE
                    ),
                    Schedule.of(
                        Duration.ofMillis(300),
                        Schedule.ExecutionType.DEFAULT
                    ),
                    Schedule.of(
                        Duration.ofHours(12),
                        Schedule.ExecutionType.FIXED_DELAYED
                    ),
                    Schedule.of(
                        Duration.ofSeconds(10),
                        Schedule.ExecutionType.FIXED_RATE
                    )
                ),
                scheduler,
                timeSynchronizer
            )
        };

        for (var task : tasks) {
            task.run();
        }

        simulator.run(
            Instant.parse("1997-05-01T10:15:00.00Z"),
            Instant.parse("1998-05-10T10:15:00.00Z")
        );
    }

    protected Runnable createDummyRunnable(
        String taskId,
        List<Schedule> schedulesList,
        SchedulerInterface scheduler,
        TimeSynchronizerInterface timeSynchronizer
    ) {
        return new Runnable() {
            private static final int MAX_ITERATIONS = 10;
            private final Iterator<Schedule> schedulesIterator = schedulesList.listIterator();
            private Schedule currentSchedule;
            private UUID currentScheduleId;
            private int intervalIterations = 0;
            private boolean finished = false;
            private boolean started = false;

            @Override
            public void run() {
                if (!started) {
                    start();
                } else {
                    execute();
                }

                if (null == currentSchedule) {
                    Assertions.assertTrue(finished);
                }
            }

            private void start() {
                if (schedulesIterator.hasNext()) {
                    started = true;
                    currentSchedule = schedulesIterator.next();

                    if (null == currentSchedule.getStartTime()) {
                        throw new IllegalArgumentException("Start time must be set on start schedule");
                    }

                    currentScheduleId = scheduler.schedule(this, currentSchedule);
                } else {
                    throw new IllegalArgumentException("No schedules to test");
                }
            }

            private void execute() {
                Assertions.assertFalse(finished);
                assertSchedule(currentSchedule, timeSynchronizer.currentTime());

                System.out.printf("[%s]: CallTime: %s", taskId, timeSynchronizer.currentTime());
                System.out.println();

                if (intervalIterations == MAX_ITERATIONS) {
                    scheduler.stop(currentScheduleId, true);
                    moveToNextSchedule();
                } else if (Schedule.ExecutionType.DEFAULT == currentSchedule.getExecutionType()) {
                    moveToNextSchedule();
                }

                intervalIterations++;
            }

            private void assertSchedule(Schedule schedule, Instant currentTime) {
                var startTime = schedule.getStartTime();
                var interval = schedule.getInterval();
                var expected = startTime.plus(interval.multipliedBy(schedule.getTotalCalls()));
                Assertions.assertEquals(expected, currentTime);
            }

            private void moveToNextSchedule() {
                System.out.printf("[%s]: Schedule with interval %s ended", taskId, currentSchedule.getInterval());
                System.out.println();
                intervalIterations = 0;

                if (schedulesIterator.hasNext()) {
                    var nextSchedule = schedulesIterator.next();
                    currentSchedule = Schedule.of(
                        timeSynchronizer.currentTime().plus(currentSchedule.getInterval()),
                        nextSchedule.getInterval(),
                        nextSchedule.getExecutionType()
                    );
                    currentScheduleId = scheduler.schedule(this, currentSchedule);
                } else {
                    currentSchedule = null;
                    currentScheduleId = null;
                    finished = true;
                }
            }
        };
    }
}
