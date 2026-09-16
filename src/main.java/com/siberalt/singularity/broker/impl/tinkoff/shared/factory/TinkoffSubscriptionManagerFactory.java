package com.siberalt.singularity.broker.impl.tinkoff.shared.factory;

import com.siberalt.singularity.entity.instrument.InstrumentIdResolver;
import com.siberalt.singularity.event.subscription.SubscriptionManager;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.streaming.StreamManagerFactory;
import ru.ttech.piapi.core.connector.streaming.StreamServiceStubFactory;

import java.util.concurrent.Executors;

/**
 * @see com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffCandleSubscriptionManager for why
 * live candles need to know our instrument ids
 */
public class TinkoffSubscriptionManagerFactory implements TinkoffServiceFactory<SubscriptionManager> {
    private final InstrumentIdResolver instrumentIds;

    public TinkoffSubscriptionManagerFactory(InstrumentIdResolver instrumentIds) {
        this.instrumentIds = instrumentIds;
    }

    @Override
    public SubscriptionManager create(ServiceStubFactory serviceStubFactory) {
        var streamFactory = StreamServiceStubFactory.create(serviceStubFactory);
        var streamManagerFactory = StreamManagerFactory.create(streamFactory);

        var executorService = Executors.newCachedThreadPool();
        var scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        var marketDataStreamManager = streamManagerFactory.newMarketDataStreamManager(executorService, scheduledExecutorService);

        return new com.siberalt.singularity.broker.impl.tinkoff.shared.TinkoffCandleSubscriptionManager(
            marketDataStreamManager, instrumentIds);
    }
}
