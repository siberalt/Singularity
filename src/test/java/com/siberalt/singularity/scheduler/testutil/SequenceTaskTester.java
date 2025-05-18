package com.siberalt.singularity.scheduler.testutil;

import com.siberalt.singularity.scheduler.ExecutionIterator;
import com.siberalt.singularity.scheduler.Schedule;
import com.siberalt.singularity.scheduler.Scheduler;
import com.siberalt.singularity.scheduler.simulation.timer.TimedRunnableDecorator;
import com.siberalt.singularity.strategy.context.Clock;
import org.junit.jupiter.api.Assertions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SequenceTaskTester implements Runnable {
    private final List<TestExecution> testExecutions = new ArrayList<>();
    private final Scheduler<String> scheduler;
    private final String taskId;
    private final Clock clock;
    private int scheduleIncrement = 0;
    private Instant lastExecutionTime = null;
    private boolean finished = false;

    public SequenceTaskTester(
        String taskId,
        Scheduler<String> scheduler,
        Clock clock
    ) {
        this.scheduler = scheduler;
        this.taskId = taskId;
        this.clock = clock;
    }

    public void add(TestExecution testExecution) {
        testExecutions.add(testExecution);
    }

    public void test() throws ExecutionException, InterruptedException {
        for (TestExecution testExecution : testExecutions) {
            Schedule<String> schedule = createSchedule(testExecution.executionIterator());
            System.out.printf("[%s]: Start %s\n", schedule.id(), schedule);
            Runnable task = () -> run(schedule.id(), clock.currentTime());

            if (testExecution.executionTime() != null) {
                task = new TimedRunnableDecorator(task, testExecution.executionTime().toMillis());
            }

            Future<?> future = scheduler.schedule(task, schedule);
            future.get();

            Assertions.assertFalse(
                future.isCancelled(),
                String.format("Schedule %s was cancelled", schedule.id())
            );
            Assertions.assertTrue(
                future.isDone(),
                String.format("Schedule %s is not done", schedule.id())
            );
            System.out.printf("[%s]: Schedule %s has ended\n", schedule.id(), schedule.id());
        }

        finished = true;
    }

    private Schedule<String> createSchedule(ExecutionIterator executionIterator) {
        return new Schedule<>(
            String.format("%s-%d", taskId, scheduleIncrement++),
            new ExecutionIteratorAsserter(executionIterator)
        );
    }

    private void run(String currentScheduleId, Instant currentTime) {
        if (lastExecutionTime != null) {
            Assertions.assertTrue(
                lastExecutionTime.equals(currentTime) || lastExecutionTime.isBefore(currentTime)
            );
        }

        lastExecutionTime = currentTime;
        System.out.printf("[%s]: CallTime: %s\n", currentScheduleId, currentTime);
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public void run() {
        try {
            test();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
