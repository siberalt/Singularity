package com.siberalt.singularity.scheduler.testutil;

import com.siberalt.singularity.scheduler.Schedule;
import com.siberalt.singularity.scheduler.Scheduler;
import org.junit.jupiter.api.Assertions;

public class StopTaskTester {
    private final Scheduler<String> scheduler;
    private final Schedule<String> schedule;
    private int iterations;

    public StopTaskTester(Scheduler<String> scheduler, Schedule<String> schedule) {
        this.scheduler = scheduler;
        this.schedule = schedule;
    }

    public void test(int stopAtIteration) {
        iterations = 0;
        Runnable task = () -> {
            if (iterations < stopAtIteration) {
                System.out.printf("[%s]: Executing task iteration %d\n", schedule.id(), iterations);
            } else if (iterations == stopAtIteration) {
                System.out.printf("[%s]: Stopping task after %d iterations\n", schedule.id(), iterations);
                scheduler.stop(schedule.id());
            } else {
                Assertions.fail("Task executed more than expected iterations");
            }

            iterations++;
        };

        scheduler.schedule(task, schedule);
    }
}
