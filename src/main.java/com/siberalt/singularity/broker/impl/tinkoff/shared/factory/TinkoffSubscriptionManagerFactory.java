package com.siberalt.singularity.broker.impl.tinkoff.shared.factory;

import com.siberalt.singularity.event.subscription.SubscriptionManager;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.streaming.StreamManagerFactory;
import ru.ttech.piapi.core.connector.streaming.StreamServiceStubFactory;

import java.util.concurrent.Executors;

public class TinkoffSubscriptionManagerFactory implements TinkoffServiceFactory<SubscriptionManager> {
    @Override
    public SubscriptionManager create(ServiceStubFactory serviceStubFactory) {
        var streamFactory = StreamServiceStubFactory.create(serviceStubFactory);
        var streamManagerFactory = StreamManagerFactory.create(streamFactory);

        var executorService = Executors.newCachedThreadPool();
        var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        var marketDataStreamManager = streamManagerFactory.newMarketDataStreamManager(executorService, scheduledExecutorService);

        return new com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffCandleSubscriptionManager(marketDataStreamManager);
    }
}
