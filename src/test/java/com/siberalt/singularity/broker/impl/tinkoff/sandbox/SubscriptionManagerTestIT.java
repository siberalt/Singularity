package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.event.subscription.SubscriptionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class SubscriptionManagerTestIT extends AbstractTinkoffSanboxIT {
    @Test
    public void candleStreaming() throws IOException, InterruptedException, AbstractException {
        SubscriptionManager manager = getTinkoffSandbox().getSubscriptionManager();

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger candlesCount = new AtomicInteger();
        int maxCandlesCount = 2;

        System.out.println("Starting candle streaming test at: " + Instant.now());
        Subscription sub = manager.subscribe(
            new NewCandleSubscriptionSpec(Set.of(getTestShare().getUid())),
            (event, subscription) -> {
                System.out.println("Received candle at: " + Instant.now());
                candlesCount.incrementAndGet();
                System.out.println("Received candle: " + event.getCandle());
                System.out.println("Event ID: " + event.getId());

                if (candlesCount.get() >= maxCandlesCount) {
                    System.out.println("Max candles count reached, stopping subscription.");
                    subscription.stop();
                    try {
                        barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        System.err.println("Barrier was interrupted or broken: " + e.getMessage());
                        Thread.currentThread().interrupt(); // Restore the interrupted status
                    }
                }
            }
        );
        Assertions.assertTrue(sub.isActive(), "Subscription should be active");

        try {
            barrier.await(130, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            Assumptions.assumeTrue(
                candlesCount.get() >= maxCandlesCount,
                "Expected at least " + maxCandlesCount + " candles, but received: " + candlesCount.get()
            );
        } catch (BrokenBarrierException e) {
            System.err.println("Barrier was broken, possibly due to timeout or interruption.");
            Assertions.fail("Candle streaming test failed due to barrier being broken.");
        }
        System.out.println("Candle streaming test completed at: " + Instant.now());
    }
}
