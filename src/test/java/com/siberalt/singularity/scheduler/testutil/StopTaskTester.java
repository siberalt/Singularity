package com.siberalt.singularity.scheduler.testutil;

import com.siberalt.singularity.scheduler.Schedule;
import com.siberalt.singularity.scheduler.Scheduler;
import com.siberalt.singularity.strategy.context.Clock;
import org.junit.jupiter.api.Assertions;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class StopTaskTester {
    private final Scheduler<String> scheduler;
    private final Schedule<String> schedule;
    private int iterations;
    private Future<?> future;
    private final Clock clock;

    public StopTaskTester(Scheduler<String> scheduler, Schedule<String> schedule, Clock clock) {
        this.scheduler = scheduler;
        this.schedule = schedule;
        this.clock = clock;
    }

    public void test(int stopAtIteration) throws ExecutionException, InterruptedException {
        iterations = 0;
        Runnable task = () -> {
            if (iterations < stopAtIteration) {
                iterations++;
                System.out.printf("[%s][%s]: Executing task iteration %d\n", schedule.id(), clock.currentTime(), iterations);
            } else if (iterations == stopAtIteration) {
                System.out.printf("[%s][%s]: Stopping task after %d iterations\n", schedule.id(), clock.currentTime(), iterations);
                future.cancel(true);
                iterations++;
            } else {
                Assertions.fail("Task executed more than expected iterations");
            }
        };

        future = scheduler.schedule(task, schedule);
        try {
            future.get();
        } catch (CancellationException exception) {
            System.out.printf("[%s]: Task was cancelled after %d iterations\n", schedule.id(), iterations - 1);
        } catch (ExecutionException exception) {
            System.out.printf("[%s]: Task execution failed: %s\n", schedule.id(), exception.getMessage());
            throw exception;
        }
        Assertions.assertTrue(
            future.isCancelled(),
            String.format("Schedule %s was not cancelled", schedule.id())
        );
        Assertions.assertFalse(
            future.isDone(),
            String.format("Schedule %s is done", schedule.id())
        );
    }
}
