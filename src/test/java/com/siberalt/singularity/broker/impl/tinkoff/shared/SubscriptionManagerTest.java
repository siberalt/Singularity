package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.event.EventDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.stream.MarketDataSubscriptionService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SubscriptionManagerTest {
    @Mock
    private InvestApi api;

    @Mock
    private MarketDataSubscriptionService marketDataSubscriptionService;

    @Mock
    private EventDispatcher eventDispatcher;

    @InjectMocks
    private SubscriptionManager subscriptionManager;

    @BeforeEach
    void setUp() {
        when(api.getMarketDataStreamService().newStream(anyString(), any(), any()))
            .thenReturn(marketDataSubscriptionService);
    }

//    @Test
//    void enableAddsSubscriptionDataToMap() {
//        NewCandleSubscriptionSpec spec = mock(NewCandleSubscriptionSpec.class);
//        when(spec.getInstrumentIds()).thenReturn(Set.of("instrument1", "instrument2"));
//
//        subscriptionManager.subscribe()
//
//        subscriptionManager.eventManager.getTriggerManager().enable(spec, eventDispatcher);
//
//        assertTrue(subscriptionManager.subscriptions.containsKey(spec));
//    }
//
//    @Test
//    void disableRemovesSubscriptionDataFromMap() {
//        NewCandleSubscriptionSpec spec = mock(NewCandleSubscriptionSpec.class);
//        when(spec.getInstrumentIds()).thenReturn(Set.of("instrument1", "instrument2"));
//
//        subscriptionManager.eventManager.getTriggerManager().enable(spec, eventDispatcher);
//        subscriptionManager.eventManager.getTriggerManager().disable(spec);
//
//        assertFalse(subscriptionManager.subscriptions.containsKey(spec));
//    }
//
//    @Test
//    void isEnabledReturnsTrueForEnabledSubscription() {
//        NewCandleSubscriptionSpec spec = mock(NewCandleSubscriptionSpec.class);
//        when(spec.getInstrumentIds()).thenReturn(Set.of("instrument1", "instrument2"));
//
//        subscriptionManager.eventManager.getTriggerManager().enable(spec, eventDispatcher);
//
//        assertTrue(subscriptionManager.eventManager.getTriggerManager().isEnabled(spec));
//    }
//
//    @Test
//    void isEnabledReturnsFalseForDisabledSubscription() {
//        NewCandleSubscriptionSpec spec = mock(NewCandleSubscriptionSpec.class);
//        when(spec.getInstrumentIds()).thenReturn(Set.of("instrument1", "instrument2"));
//
//        subscriptionManager.eventManager.getTriggerManager().enable(spec, eventDispatcher);
//        subscriptionManager.eventManager.getTriggerManager().disable(spec);
//
//        assertFalse(subscriptionManager.eventManager.getTriggerManager().isEnabled(spec));
//    }
//
//    @Test
//    void subscribeToCandleEventsDispatchesNewCandleEvent() {
//        NewCandleSubscriptionSpec spec = mock(NewCandleSubscriptionSpec.class);
//        when(spec.getInstrumentIds()).thenReturn(Set.of("instrument1"));
//
//        UUID subscriptionId = UUID.randomUUID();
//        subscriptionManager.subscribeToCandleEvents(spec, eventDispatcher, subscriptionId);
//
//        verify(marketDataSubscriptionService).subscribeCandles(List.of("instrument1"));
//    }
}
