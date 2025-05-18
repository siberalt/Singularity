package com.siberalt.singularity.simulation.synch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class TaskSynchronizer {
    private static final Logger logger = LoggerFactory.getLogger(TaskSynchronizer.class);

    private final Set<Runnable> tasks = new HashSet<>();
    private final Thread simulatorThread;
    private int suspendedTasksCount;
    private int terminatedTasksCount;
    private final Object lock = new Object();
    private final Set<Thread> threads = new HashSet<>();
    private final Map<Thread, Throwable> uncaughtExceptions = new HashMap<>();
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
        suspendedTasksCount = 0;
        terminatedTasksCount = 0;
        for (Runnable task : tasks) {
            Thread thread = new Thread(
                () -> {
                    try {
                        task.run();
                    } finally {
                        synchronized (lock) {
                            terminatedTasksCount++;

                            if (shouldNotWait()) {
                                lock.notify();
                            }
                        }
                    }
                }
            );
            thread.setUncaughtExceptionHandler(this::handleException);
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

        try {
            synchronized (lock) {
                logger.debug("Simulator thread is waiting for all tasks to suspend.");
                lock.wait();
                logger.debug("Simulator thread is notified. Continuing simulation.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Task was interrupted while waiting", e);
        }
    }

    public void resumeThread(Thread thread) {
        if (thread == simulatorThread) {
            throw new IllegalStateException("Cannot resume the simulator thread");
        }

        validateThreadRegistration(thread);

        synchronized (thread) {
            suspendedTasksCount--;
            thread.notify();
        }
    }

    public boolean suspendThread(Thread thread, long timeout, TimeUnit unit) {
        if (thread == simulatorThread) {
            throw new IllegalStateException("Cannot suspend the simulator thread");
        }

        validateThreadRegistration(thread);

        boolean isTimedOut = false;
        try {
            synchronized (thread) {
                suspendedTasksCount++;
                long timeoutNanos = unit.toNanos(timeout);
                Duration duration = Duration.ofNanos(timeoutNanos);
                long startTime = System.nanoTime();

                thread.wait(duration.toMillisPart(), duration.toNanosPart());

                long elapsedTime = System.nanoTime() - startTime;
                if (elapsedTime >= timeoutNanos) {
                    isTimedOut = true;
                }

                // Notify the simulator thread when all threads are suspended
                if (shouldNotWait()) {
                    lock.notify();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return isTimedOut;
    }

    public void suspendThread(Thread thread) {
        if (thread == simulatorThread) {
            throw new IllegalStateException("Cannot suspend the simulator thread");
        }

        validateThreadRegistration(thread);

        try {
            synchronized (thread) {
                suspendedTasksCount++;

                // Notify the simulator thread when all threads are suspended
                if (shouldNotWait()) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }

                thread.wait();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void validateThreadRegistration(Thread thread) {
        if (!threads.contains(thread)) {
            throw new IllegalStateException("Thread not registered: " + thread);
        }
    }

    private boolean shouldNotWait() {
        return suspendedTasksCount + terminatedTasksCount == tasks.size();
    }

    private void handleException(Thread thread, Throwable e) {
        logger.error("Uncaught exception in thread {}: {}", thread.getName(), e.getMessage(), e);
        uncaughtExceptions.put(thread, e);
    }
}
