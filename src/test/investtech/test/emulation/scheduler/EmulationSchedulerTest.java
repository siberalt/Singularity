package investtech.test.emulation.scheduler;

import investtech.emulation.EventObserver;
import investtech.emulation.TimeFlowEmulator;
import investtech.strategy.StrategyInterface;
import investtech.strategy.context.AbstractContext;
import investtech.strategy.context.emulation.EmulationContext;
import investtech.strategy.context.emulation.time.EmulationTimeSynchronizer;
import investtech.strategy.scheduler.Schedule;
import investtech.strategy.scheduler.emulation.EmulationScheduler;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

public class EmulationSchedulerTest {
    protected EmulationScheduler scheduler;

    protected EmulationTimeSynchronizer timeSynchronizer;

    protected EventObserver eventObserver;

    protected TimeFlowEmulator timeFlowEmulator;

    protected AbstractContext<?> context;

    @Test
    public void testSingle() {
        init();

        System.out.println("Testing single strategy on run:");

        var strategy = createChangingScheduledStrategy(
                Instant.parse("1997-05-07T10:15:00.00Z"),
                "strategy1",
                List.of(
                        ScheduleDurationItem.of(
                                Duration.ofMinutes(30),
                                Schedule.ExecutionType.DEFAULT
                        ),
                        ScheduleDurationItem.of(
                                Duration.ofDays(3),
                                Schedule.ExecutionType.FIXED_RATE
                        ),
                        ScheduleDurationItem.of(
                                Duration.ofSeconds(25),
                                Schedule.ExecutionType.FIXED_RATE
                        ),
                        ScheduleDurationItem.of(
                                Duration.ofMinutes(30),
                                Schedule.ExecutionType.FIXED_RATE
                        )
                ).listIterator(),
                10
        );

        strategy.start(context);
        timeFlowEmulator.run(
                Instant.parse("1997-05-01T10:15:00.00Z"),
                Instant.parse("1998-05-10T10:15:00.00Z")
        );
    }

    @Test
    public void testMultiple() {
        init();

        System.out.println("Testing multiple strategies on run:");

        StrategyInterface[] strategies = new StrategyInterface[]{
                createChangingScheduledStrategy(
                        Instant.parse("1997-05-07T00:00:00.00Z"),
                        "strategy1",
                        List.of(
                                ScheduleDurationItem.of(
                                        Duration.ofSeconds(10),
                                        Schedule.ExecutionType.DEFAULT
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofHours(2),
                                        Schedule.ExecutionType.FIXED_RATE
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofMillis(10),
                                        Schedule.ExecutionType.FIXED_DELAYED
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofMinutes(20),
                                        Schedule.ExecutionType.DEFAULT
                                )
                        ).listIterator(),
                        10
                ),
                createChangingScheduledStrategy(
                        Instant.parse("1997-05-08T10:00:00.00Z"),
                        "strategy2",
                        List.of(
                                ScheduleDurationItem.of(
                                        Duration.ofSeconds(10),
                                        Schedule.ExecutionType.DEFAULT
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofMillis(150),
                                        Schedule.ExecutionType.FIXED_RATE
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofSeconds(60),
                                        Schedule.ExecutionType.FIXED_DELAYED
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofDays(10),
                                        Schedule.ExecutionType.DEFAULT
                                )
                        ).listIterator(),
                        10
                ),
                createChangingScheduledStrategy(
                        Instant.parse("1997-05-08T10:00:00.00Z"),
                        "strategy3",
                        List.of(
                                ScheduleDurationItem.of(
                                        Duration.ofSeconds(10),
                                        Schedule.ExecutionType.DEFAULT
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofMillis(150),
                                        Schedule.ExecutionType.FIXED_RATE
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofSeconds(60),
                                        Schedule.ExecutionType.FIXED_DELAYED
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofDays(10),
                                        Schedule.ExecutionType.DEFAULT
                                )
                        ).listIterator(),
                        10
                ),
                createChangingScheduledStrategy(
                        Instant.parse("1997-05-06T13:15:00.00Z"),
                        "strategy4",
                        List.of(
                                ScheduleDurationItem.of(
                                        Duration.ofMinutes(3),
                                        Schedule.ExecutionType.FIXED_RATE
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofMillis(300),
                                        Schedule.ExecutionType.DEFAULT
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofHours(12),
                                        Schedule.ExecutionType.FIXED_DELAYED
                                ),
                                ScheduleDurationItem.of(
                                        Duration.ofSeconds(10),
                                        Schedule.ExecutionType.FIXED_RATE
                                )
                        ).listIterator(),
                        10
                )
        };

        for (var strategy : strategies) {
            strategy.start(context);
        }

        timeFlowEmulator.run(
                Instant.parse("1997-05-01T10:15:00.00Z"),
                Instant.parse("1998-05-10T10:15:00.00Z")
        );
    }

    protected void init() {
        scheduler = new EmulationScheduler();
        timeSynchronizer = new EmulationTimeSynchronizer();
        eventObserver = new EventObserver();

        timeFlowEmulator = new TimeFlowEmulator(eventObserver, timeSynchronizer);
        context = new EmulationContext(scheduler, null, timeSynchronizer);
        timeFlowEmulator.addTimeDependentUnit(scheduler);

        scheduler.applyContext(context);
        scheduler.observeEventsBy(eventObserver);
    }

    protected StrategyInterface createChangingScheduledStrategy(
            Instant startTime,
            String strategyId,
            Iterator<ScheduleDurationItem> scheduleDurationItemIterator,
            int iterationsPerInterval
    ) {
        return new StrategyInterface() {
            private String id = strategyId;
            private Schedule currentSchedule;

            private int intervalIterations = 0;

            private boolean finished = false;

            @Override
            public void start(AbstractContext<?> context) {
                if (scheduleDurationItemIterator.hasNext()) {
                    var scheduleDurationItem = scheduleDurationItemIterator.next();
                    currentSchedule = Schedule.of(
                            startTime,
                            scheduleDurationItem.getInterval(),
                            scheduleDurationItem.getExecutionType()
                    );
                    context.getScheduler().schedule(this, currentSchedule);
                }
            }

            @Override
            public void run(AbstractContext<?> context) {
                Assertions.assertFalse(finished);
                assertSchedule(currentSchedule, context.getCurrentTime());

                System.out.printf("[%s]: CallTime: %s", strategyId, context.getCurrentTime());
                System.out.println();

                intervalIterations++;

                if (intervalIterations == iterationsPerInterval) {
                    context.getScheduler().stop(this, true);
                    moveToNextSchedule(context);
                } else if (Schedule.ExecutionType.DEFAULT == currentSchedule.getExecutionType()) {
                    moveToNextSchedule(context);
                }
            }

            @Override
            public void stop(AbstractContext<?> context) {
                Assertions.assertTrue(finished);
            }

            @Override
            public String getId() {
                return id;
            }

            @Override
            public void setId(String id) {
                this.id = id;
            }

            private void assertSchedule(Schedule schedule, Instant currentTime) {
                var startTime = schedule.getStartTime();
                var interval = schedule.getInterval();
                var expected = startTime.plus(interval.multipliedBy(schedule.getTotalCalls()));
                Assertions.assertEquals(expected, currentTime);
            }

            private void moveToNextSchedule(AbstractContext<?> context) {
                System.out.println("Schedule with interval " + currentSchedule.getInterval() + " ended");
                System.out.println();
                intervalIterations = 0;

                if (scheduleDurationItemIterator.hasNext()) {
                    var scheduleDurationItem = scheduleDurationItemIterator.next();
                    currentSchedule = Schedule.of(
                            context.getCurrentTime().plus(currentSchedule.getInterval()),
                            scheduleDurationItem.getInterval(),
                            scheduleDurationItem.getExecutionType()
                    );
                    context.getScheduler().schedule(this, currentSchedule);
                } else {
                    finished = true;
                }
            }
        };
    }
}
