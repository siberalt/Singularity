package com.siberalt.singularity.scheduler.simulation;

import com.siberalt.singularity.simulation.synch.TaskSynchronizer;

import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

class SimulatedScheduledFuture<idType> implements ScheduledFuture<idType> {
    private boolean cancelled = false;
    private boolean done = false;
    private Exception exception;
    private final TaskSynchronizer synchronizer;
    private final Runnable onCancel;
    private Thread thread;
    private long delay; // in nanoseconds

    public SimulatedScheduledFuture(TaskSynchronizer synchronizer, Runnable onCancel) {
        this.synchronizer = synchronizer;
        this.onCancel = onCancel;
    }

    public void setException(Exception exception) {
        this.exception = exception;
    }

    public SimulatedScheduledFuture<idType> setDelay(long delay) {
        this.delay = delay;
        return this;
    }

    protected void finish() {
        if (cancelled) {
            return;
        }

        done = true;
        if (thread != null) {
            synchronizer.resumeThread(thread);
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (cancelled) {
            return false;
        }

        cancelled = true;
        done = true;
        onCancel.run();

        return true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public idType get() throws InterruptedException, ExecutionException {
        thread = Thread.currentThread();
        synchronizer.suspendThread(thread);

        handleExceptions();

        return null;
    }

    @Override
    public idType get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        thread = Thread.currentThread();

        if (!synchronizer.suspendThread(thread, timeout, unit)) {
            throw new TimeoutException("Timeout waiting for the lock");
        }

        handleExceptions();

        return null;
    }

    private void handleExceptions() throws InterruptedException, ExecutionException {
        if (exception != null) {
            if (exception instanceof InterruptedException) {
                throw (InterruptedException) exception;
            } else if (exception instanceof ExecutionException) {
                throw (ExecutionException) exception;
            } else {
                throw new ExecutionException(exception);
            }
        }
    }

    @Override
    public long getDelay(TimeUnit unit) {
        // SimulatedScheduledFuture does not have a delay
        return unit.convert(delay, NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
        return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
    }
}
