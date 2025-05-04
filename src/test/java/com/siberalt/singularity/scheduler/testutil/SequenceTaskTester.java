package com.siberalt.singularity.scheduler.testutil;

import com.siberalt.singularity.scheduler.ExecutionIterator;
import com.siberalt.singularity.scheduler.Schedule;
import com.siberalt.singularity.scheduler.Scheduler;
import com.siberalt.singularity.scheduler.simulation.timer.TimedRunnableDecorator;
import com.siberalt.singularity.strategy.context.Clock;
import org.junit.jupiter.api.Assertions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SequenceTaskTester {
    private final List<TestExecution> testExecutions = new ArrayList<>();
    private Iterator<TestExecution> testExecutionsIterator;
    private Schedule<String> currentSchedule;
    private boolean finished = false;
    private final Scheduler<String> scheduler;
    private final String taskId;
    private final Clock clock;
    private int scheduleIncrement = 0;
    private Instant lastExecutionTime = null;

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

    public boolean isFinished() {
        return finished;
    }

    public void test() {
        testExecutionsIterator = testExecutions.iterator();
        moveToNextSchedule();
    }

    private Schedule<String> createSchedule(ExecutionIterator executionIterator) {
        return new Schedule<>(
            String.format("%s-%d", taskId, scheduleIncrement++),
            new ExecutionIteratorAsserter(executionIterator),
            this::onFinish
        );
    }

    private void onFinish(Schedule<String> schedule) {
        System.out.printf("[%s]: Schedule %s has ended\n", currentSchedule.id(), currentSchedule.id());
        Assertions.assertEquals(currentSchedule, schedule);

        moveToNextSchedule();
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

    private void moveToNextSchedule() {
        if (testExecutionsIterator.hasNext()) {
            TestExecution testExecution = testExecutionsIterator.next();
            currentSchedule = createSchedule(testExecution.executionIterator());
            System.out.printf("[%s]: Start %s\n", currentSchedule.id(), currentSchedule);
            Runnable task = () -> run(currentSchedule.id(), clock.currentTime());

            if (testExecution.executionTime() != null) {
                task = new TimedRunnableDecorator(task, testExecution.executionTime().toMillis());
            }

            scheduler.schedule(task, currentSchedule);
        } else {
            currentSchedule = null;
            finished = true;
        }
    }
}
