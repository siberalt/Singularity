package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.QuotationTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.TimestampTranslator;
import com.siberalt.singularity.broker.shared.CandleEventMatcher;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.instrument.InstrumentIdResolver;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Live candles from T-Bank as {@link NewCandleEvent}s.
 * <p>
 * The stream names instruments by T-Bank uid, and candles carry our own instrument id, so every
 * candle is translated on arrival. A candle of an instrument that has no listing is dropped with an
 * error rather than dispatched: a candle of no instrument could only be saved against nothing, and
 * a strategy reading history by id would find none of it. Registering the instrument - saving its
 * listing - is what makes its candles come through.
 */
public class TinkoffCandleSubscriptionManager implements com.siberalt.singularity.event.subscription.SubscriptionManager {
    private static final Logger logger = LoggerFactory.getLogger(TinkoffCandleSubscriptionManager.class);

    private record SubscriptionData(UUID subscriptionId, CandleSubscriptionSpec candleSubscriptionSpec) {
    }

    private final MarketDataStreamManager streamManager;
    private final InstrumentIdResolver instrumentIds;
    private final EventManager eventManager;
    private final Map<SubscriptionSpec<?>, SubscriptionData> subscriptions = new HashMap<>();

    public TinkoffCandleSubscriptionManager(MarketDataStreamManager streamManager, InstrumentIdResolver instrumentIds) {
        this.streamManager = streamManager;
        this.instrumentIds = instrumentIds;
        eventManager = new EventManager(Executors.newSingleThreadExecutor(), Set.of(NewCandleEvent.class));
        eventManager.setEventMatcher(new CandleEventMatcher());
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
                        TinkoffCandleSubscriptionManager.this.streamManager.unsubscribeCandles(
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
        OptionalLong instrumentId = instrumentIds.idOf(candle.getInstrumentUid());

        if (instrumentId.isEmpty()) {
            logger.error("Candle of {} dropped: the instrument has no listing, so it has no id to be stored under",
                candle.getInstrumentUid());
            return;
        }

        eventDispatcher.dispatch(
            new NewCandleEvent(
                UUID.randomUUID(),
                candle.getInstrumentUid(),
                Candle.of(
                    TimestampTranslator.toContract(candle.getTime()),
                    instrumentId.getAsLong(),
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
