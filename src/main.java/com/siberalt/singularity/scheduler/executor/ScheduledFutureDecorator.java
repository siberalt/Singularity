package com.siberalt.singularity.scheduler.executor;

import com.siberalt.singularity.strategy.context.Clock;

import java.util.concurrent.*;

@SuppressWarnings("unchecked")
class ScheduledFutureDecorator<resultType> implements ScheduledFuture<resultType> {
    private ScheduledFuture<?> future;
    private ScheduledFuture<?> newFuture;
    private final Runnable onCancel;
    private boolean ignoreCancellation = false;
    private boolean isDone = false;
    private boolean isCancelled = false;
    private final Clock clock;

    public ScheduledFutureDecorator(Runnable onCancel, Clock clock) {
        this.onCancel = onCancel;
        this.clock = clock;
    }

    public void replace(ScheduledFuture<?> newFuture) {
        if (isCancelled || isDone) {
            return;
        }
        ensureFutureInitialized(newFuture);
        if (this.future != null) {
            this.ignoreCancellation = true;
            this.future.cancel(false);
        }
        this.newFuture = newFuture;
    }

    private void ensureFutureInitialized(ScheduledFuture<?> newFuture) {
        if (newFuture == null) {
            throw new IllegalStateException("Future is not initialized. Call decorate() first.");
        }
    }

    public void finish() {
        try {
            if (isDone) {
                return;
            }
            isDone = true;
            future.cancel(false);
        } finally {
            onCancel.run();
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        try {
            if (isCancelled || isDone) {
                return false;
            }

            if (future == null) {
                future = newFuture;
                newFuture = null;
            }
            isCancelled = true;
            return future.cancel(mayInterruptIfRunning);
        } finally {
            onCancel.run();
        }
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public boolean isDone() {
        return isDone;
    }

    @Override
    public resultType get() throws InterruptedException, ExecutionException {
        resultType result = null;

        while (newFuture != null) {
            try {
                future = newFuture;
                newFuture = null;
                result = (resultType) future.get();
            } catch (CancellationException e) {
                if (newFuture == null && !isDone) {
                    isCancelled = true;
                    throw e;
                }
            }
        }

        return result;
    }

    @Override
    public resultType get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
        long remainingTimeout = unit.toMillis(timeout); // Convert timeout to milliseconds
        resultType result = null;

        while (newFuture != null) {
            try {
                long startTime = clock.currentTime().toEpochMilli();
                future = newFuture;
                newFuture = null;
                result = (resultType) future.get(timeout, unit);
                long elapsedTime = clock.currentTime().toEpochMilli() - startTime;
                remainingTimeout -= elapsedTime; // Update remaining timeout

                if (remainingTimeout <= 0) {
                    throw new TimeoutException("Timeout exceeded");
                }
            } catch (CancellationException e) {
                if (newFuture == null && !isDone) {
                    isCancelled = true;
                    throw e;
                }
            }
        }

        return result;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return future.getDelay(unit);
    }

    @Override
    public int compareTo(Delayed o) {
        return future.compareTo(o);
    }

    @Override
    public String toString() {
        return "FutureDecorator{" +
            "future=" + future +
            ", ignoreCancellation=" + ignoreCancellation +
            '}';
    }
}
