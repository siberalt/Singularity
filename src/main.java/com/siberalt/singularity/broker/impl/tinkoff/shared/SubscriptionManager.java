package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.QuotationTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.TimestampTranslator;
import com.siberalt.singularity.broker.shared.EventMatcher;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.event.Event;
import com.siberalt.singularity.event.EventDispatcher;
import com.siberalt.singularity.event.EventHandler;
import com.siberalt.singularity.event.EventManager;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;
import com.siberalt.singularity.event.trigger.TriggerManager;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.stream.MarketDataSubscriptionService;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;

public class SubscriptionManager implements com.siberalt.singularity.event.subscription.SubscriptionManager {
    private record SubscriptionData(UUID subscriptionId, MarketDataSubscriptionService marketDataSubscription) {
    }

    private final InvestApi api;
    private final EventManager eventManager;
    private final Map<SubscriptionSpec<?>, SubscriptionData> subscriptions = new HashMap<>();

    public SubscriptionManager(InvestApi api) {
        this.api = api;
        eventManager = new EventManager(Executors.newSingleThreadExecutor(), Set.of(NewCandleEvent.class));
        eventManager.setEventMatcher(new EventMatcher());
        eventManager.setTriggerManager(new TriggerManager() {
            @Override
            public void enable(SubscriptionSpec<?> subscriptionSpec, EventDispatcher eventDispatcher) {
                UUID subscriptionId = UUID.randomUUID();
                MarketDataSubscriptionService marketDataSubscription = null;

                if (subscriptionSpec instanceof NewCandleSubscriptionSpec spec) {
                    marketDataSubscription = subscribeToCandleEvents(spec, eventDispatcher, subscriptionId);
                }

                if (marketDataSubscription != null) {
                    subscriptions.put(subscriptionSpec, new SubscriptionData(subscriptionId, marketDataSubscription));
                }
            }

            @Override
            public void disable(SubscriptionSpec<?> subscriptionSpec) {
                SubscriptionData subscriptionData = subscriptions.remove(subscriptionSpec);
                if (subscriptionData != null) {
                    if (subscriptionSpec instanceof NewCandleSubscriptionSpec spec) {
                        MarketDataSubscriptionService marketDataSubscription = subscriptionData.marketDataSubscription();
                        marketDataSubscription.unsubscribeCandles(spec.getInstrumentIds().stream().toList());
                    }
                }
            }

            @Override
            public boolean isEnabled(SubscriptionSpec<?> subscriptionSpec) {
                return subscriptions.containsKey(subscriptionSpec);
            }
        });
    }

    @Override
    public <T extends Event> Subscription subscribe(SubscriptionSpec<T> spec, EventHandler<T> handler) {
        return eventManager.subscribe(spec, handler);
    }

    private MarketDataSubscriptionService subscribeToCandleEvents(
        NewCandleSubscriptionSpec subscriptionSpec,
        EventDispatcher eventDispatcher,
        UUID subscriptionId
    ) {
        MarketDataSubscriptionService marketSubscriptionService = api.getMarketDataStreamService().newStream(
            subscriptionId.toString(),
            request -> {
                if (request.hasCandle()) {
                    var candle = request.getCandle();
                    System.out.println("Received candle: " + candle);

                    eventDispatcher.dispatch(
                        new NewCandleEvent(
                            UUID.randomUUID(),
                            Candle.of(
                                TimestampTranslator.toContract(candle.getTime()),
                                candle.getInstrumentUid(),
                                candle.getVolume(),
                                QuotationTranslator.toContract(candle.getOpen()),
                                QuotationTranslator.toContract(candle.getHigh()),
                                QuotationTranslator.toContract(candle.getLow()),
                                QuotationTranslator.toContract(candle.getClose())
                            )
                        )
                    );
                }
            },
            error -> System.err.println("Error in market data stream: " + error)
        );

        marketSubscriptionService.subscribeCandles(
            subscriptionSpec.getInstrumentIds().stream().toList(),
            true
        );

        return marketSubscriptionService;
    }
}
