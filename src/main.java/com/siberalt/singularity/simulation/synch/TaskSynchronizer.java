package com.siberalt.singularity.simulation.synch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class TaskSynchronizer {
    private static final Logger logger = LoggerFactory.getLogger(TaskSynchronizer.class);

    private final Set<Runnable> tasks = new HashSet<>();
    private final Thread simulatorThread;
    private final AtomicInteger suspendedTasksCount = new AtomicInteger();
    private final AtomicInteger terminatedTasksCount = new AtomicInteger();
    private final Set<Thread> threads = new HashSet<>();
    private final Map<Thread, Throwable> uncaughtExceptions = new HashMap<>();
    private final Map<Thread, ThreadState> threadStates = new HashMap<>();
    private Phaser phaser;
    private boolean ignoreUncaughtExceptions = false;

    public TaskSynchronizer(Thread simulatorThread) {
        this.simulatorThread = simulatorThread;
    }

    public boolean isIgnoreUncaughtExceptions() {
        return ignoreUncaughtExceptions;
    }

    public TaskSynchronizer setIgnoreUncaughtExceptions(boolean ignoreUncaughtExceptions) {
        this.ignoreUncaughtExceptions = ignoreUncaughtExceptions;
        return this;
    }

    public void registerTask(Runnable runnable) {
        tasks.add(runnable);
    }

    public void start() {
        suspendedTasksCount.set(0);
        terminatedTasksCount.set(0);
        phaser = new Phaser(tasks.size() + 1);
        for (Runnable task : tasks) {
            Thread thread = new Thread(() -> {
                try {
                    task.run();
                } finally {
                    terminatedTasksCount.incrementAndGet();
                    phaser.arriveAndDeregister(); // Deregister when the task is complete
                }
            });
            thread.setUncaughtExceptionHandler(this::handleException);
            threadStates.put(thread, new ThreadState());
            thread.start();
            threads.add(thread);
        }
    }

    public boolean hasTasks() {
        return !tasks.isEmpty();
    }

    public void waitForTasks() {
        if (!hasTasks()) {
            logger.debug("No tasks to wait for.");
            return;
        }

        if (simulatorThread != Thread.currentThread()) {
            throw new IllegalStateException("Only the simulator thread can call this method");
        }

        if (shouldNotWait()) {
            logger.debug("All tasks are suspended or terminated. Continuing simulator thread.");
            return;
        }

        if (!uncaughtExceptions.isEmpty() && !ignoreUncaughtExceptions) {
            RuntimeException compositeException = new RuntimeException("Uncaught exceptions occurred in threads");
            uncaughtExceptions.values().forEach(compositeException::addSuppressed);
            throw compositeException;
        }

        logger.debug("Simulator thread is waiting for all tasks to suspend.");
        phaser.arriveAndAwaitAdvance(); // Wait for all threads to reach this phase
        logger.debug("Simulator thread is notified. Continuing simulation.");
    }

    public void resumeThread(Thread thread) {
        validateThread(thread);

        ThreadState state = threadStates.get(thread);
        if (state != null) {
            phaser.register();
            suspendedTasksCount.decrementAndGet();
            LockSupport.unpark(thread);
        }
    }

    public boolean suspendThread(Thread thread, long timeout, TimeUnit unit) {
        if (thread == simulatorThread) {
            throw new IllegalStateException("Cannot suspend the simulator thread");
        }

        validateThread(thread);

        boolean isTimedOut = false;
        try {
            suspendedTasksCount.incrementAndGet();
            long timeoutNanos = unit.toNanos(timeout);
            long startTime = System.nanoTime();

            phaser.arriveAndDeregister();
            LockSupport.parkNanos(timeoutNanos);

            long elapsedTime = System.nanoTime() - startTime;
            if (elapsedTime >= timeoutNanos) {
                isTimedOut = true;
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }

        return isTimedOut;
    }

    public void suspendThread(Thread thread) {
        if (thread == simulatorThread) {
            throw new IllegalStateException("Cannot suspend the simulator thread");
        }

        synchronized (this) {
            validateThread(thread);
            suspendedTasksCount.incrementAndGet();
            // Notify the simulator thread when all threads are suspended
            phaser.arriveAndDeregister();
        }
        LockSupport.park();
    }

    private void validateThread(Thread thread) {
        if (thread == simulatorThread) {
            throw new IllegalStateException("Cannot resume the simulator thread");
        }

        if (!threads.contains(thread)) {
            throw new IllegalStateException("Thread not registered: " + thread);
        }
    }

    private boolean shouldNotWait() {
        return suspendedTasksCount.get() + terminatedTasksCount.get() == tasks.size();
    }

    private void handleException(Thread thread, Throwable e) {
        logger.error("Uncaught exception in thread {}: {}", thread.getName(), e.getMessage(), e);
        uncaughtExceptions.put(thread, e);
    }

    private static class ThreadState {
        // Placeholder for additional thread state management if needed
    }
}
