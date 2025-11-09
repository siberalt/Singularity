package com.siberalt.singularity.strategy.impl;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.shared.EventSubscriptionBrokerFacade;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BasicTradeStrategyTest {

    @Mock
    private EventSubscriptionBrokerFacade broker;
    @Mock
    private UpsideCalculator upsideCalculator;
    @Mock
    private ReadCandleRepository candleRepository;
    @Mock
    private Subscription subscription;
    @Mock
    private NewCandleEvent event;

    private BasicTradeStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new BasicTradeStrategy(
            broker,
            "instrumentId",
            "accountId",
            upsideCalculator,
            candleRepository
        );
    }

    @Test
    void doesNotProcessCandleWithDifferentInstrumentId() {
        Candle candle = Candle.of(
            Instant.parse("2023-01-01T00:00:00Z"), "differentInstrumentId", 100L, 25
        );
        when(event.getCandle()).thenReturn(candle);

        strategy.handleNewCandle(event, subscription);

        verifyNoInteractions(candleRepository, upsideCalculator, broker);
    }

    @Test
    void processesCandleAndExecutesBuyWhenUpsideSignalExceedsThreshold() throws AbstractException {
        Candle candle1 = Candle.of(
            Instant.parse("2023-01-01T00:00:00Z"), "instrumentId", 100L, 25
        );
        when(event.getCandle()).thenReturn(candle1);
        when(candleRepository.findBeforeOrEqual(anyString(), any(), anyLong())).thenReturn(List.of(candle1));
        when(upsideCalculator.calculate(anyList())).thenReturn(new Upside(0.7, 1.0));

        strategy.setBuyThreshold(0.6);
        strategy.setTradePeriodCandles(1);
        strategy.handleNewCandle(event, subscription);

        verify(broker).buyBestPriceFullBalance("accountId", "instrumentId");
    }

    @Test
    void processesCandleAndExecutesSellWhenUpsideSignalFallsBelowThreshold() throws AbstractException {
        Candle candle1 = Candle.of(
            Instant.parse("2023-01-01T00:00:00Z"), "instrumentId", 100L, 25
        );
        when(event.getCandle()).thenReturn(candle1);
        when(candleRepository.findBeforeOrEqual(anyString(), any(), anyLong())).thenReturn(List.of(candle1));
        when(upsideCalculator.calculate(anyList())).thenReturn(new Upside(-0.6, 1.0));
        when(broker.getPositionSize("accountId", "instrumentId")).thenReturn(100L);

        strategy.setSellThreshold(-0.5);
        strategy.setTradePeriodCandles(1);
        strategy.handleNewCandle(event, subscription);

        verify(broker).sellBestPrice("accountId", "instrumentId", 100L);
    }

    @Test
    void doesNotExecuteTradeWhenUpsideSignalIsWithinThresholds() {
        NewCandleEvent event1 = new NewCandleEvent(
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), "instrumentId", 100L, 25)
        );
        NewCandleEvent event2 = new NewCandleEvent(
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), "instrumentId", 100L, 26)
        );
        when(upsideCalculator.calculate(anyList())).thenReturn(new Upside(0.0, 1.0));

        strategy.setBuyThreshold(0.6);
        strategy.setSellThreshold(-0.5);
        strategy.setTradePeriodCandles(1);
        strategy.handleNewCandle(event1, subscription);
        strategy.handleNewCandle(event2, subscription);

        verifyNoInteractions(broker);
    }
}
