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
import ru.tinkoff.piapi.contract.v1.GetCandlesRequest;
import ru.ttech.piapi.core.impl.marketdata.MarketDataStreamManager;
import ru.ttech.piapi.core.impl.marketdata.subscription.CandleSubscriptionSpec;
import ru.ttech.piapi.core.impl.marketdata.subscription.Instrument;
import ru.ttech.piapi.core.impl.marketdata.wrapper.CandleWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class SubscriptionManager implements com.siberalt.singularity.event.subscription.SubscriptionManager {
    private record SubscriptionData(UUID subscriptionId, CandleSubscriptionSpec candleSubscriptionSpec) {
    }

    private final MarketDataStreamManager streamManager;
    private final EventManager eventManager;
    private final Map<SubscriptionSpec<?>, SubscriptionData> subscriptions = new HashMap<>();

    public SubscriptionManager(MarketDataStreamManager streamManager) {
        this.streamManager = streamManager;
        eventManager = new EventManager(Executors.newSingleThreadExecutor(), Set.of(NewCandleEvent.class));
        eventManager.setEventMatcher(new EventMatcher());
        eventManager.setTriggerManager(new TriggerManager() {
            @Override
            public void enable(SubscriptionSpec<?> subscriptionSpec, EventDispatcher eventDispatcher) {
                UUID subscriptionId = UUID.randomUUID();

                if (subscriptionSpec instanceof NewCandleSubscriptionSpec spec) {
                    CandleSubscriptionSpec candleSubscriptionSpec = subscribeToCandleEvents(spec, eventDispatcher);
                    subscriptions.put(subscriptionSpec, new SubscriptionData(subscriptionId, candleSubscriptionSpec));
                }
            }

            @Override
            public void disable(SubscriptionSpec<?> subscriptionSpec) {
                SubscriptionData subscriptionData = subscriptions.remove(subscriptionSpec);
                if (subscriptionData != null) {
                    if (subscriptionSpec instanceof NewCandleSubscriptionSpec spec) {
                        SubscriptionManager.this.streamManager.unsubscribeCandles(
                            spec.getInstrumentIds().stream().map(Instrument::new).collect(Collectors.toSet()),
                            subscriptionData.candleSubscriptionSpec()
                        );
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

    private CandleSubscriptionSpec subscribeToCandleEvents(NewCandleSubscriptionSpec subscriptionSpec, EventDispatcher eventDispatcher) {
        CandleSubscriptionSpec candleSubscriptionSpec = new CandleSubscriptionSpec(GetCandlesRequest.CandleSource.CANDLE_SOURCE_INCLUDE_WEEKEND);

        streamManager.subscribeCandles(
            subscriptionSpec.getInstrumentIds().stream().map(Instrument::new).collect(Collectors.toSet()),
            candleSubscriptionSpec,
            candleWrapper -> proceedNewCandle(candleWrapper, eventDispatcher)
        );
        streamManager.start();

        return candleSubscriptionSpec;
    }

    private void proceedNewCandle(CandleWrapper candleWrapper, EventDispatcher eventDispatcher) {
        System.out.println("Received candle: " + candleWrapper.getInstrumentUid());

        ru.tinkoff.piapi.contract.v1.Candle candle = candleWrapper.getOriginal();

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
}
