package com.siberalt.singularity.strategy.impl;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.shared.EventSubscriptionBrokerFacade;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalSource;
import com.siberalt.singularity.strategy.signal.SignalType;
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
    private SignalSource signalSource;
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
            signalSource,
            candleRepository
        );
    }

    @Test
    void doesNotProcessCandleWithDifferentInstrumentId() {
        Candle candle = Candle.of(
            Instant.parse("2023-01-01T00:00:00Z"), 1L, 100L, 25
        );
        when(event.getInstrumentUid()).thenReturn("differentInstrumentId");

        strategy.handleNewCandle(event, subscription);

        verifyNoInteractions(candleRepository, signalSource, broker);
    }

    @Test
    void processesCandleAndExecutesBuyWhenSignalExceedsThreshold() throws AbstractException {
        Candle candle1 = Candle.of(
            Instant.parse("2023-01-01T00:00:00Z"), 2L, 100L, 25
        );
        when(event.getInstrumentUid()).thenReturn("instrumentId");
        when(event.getCandle()).thenReturn(candle1);
        when(candleRepository.findBeforeOrEqual(anyLong(), any(), anyLong())).thenReturn(List.of(candle1));
        when(signalSource.calculate(anyList())).thenReturn(new Signal(0.7, 1.0));
        when(broker.getMaxBuyQuantity("accountId", "instrumentId", OrderType.BEST_PRICE))
            .thenReturn(100L);

        strategy.setBuyThreshold(0.6);
        strategy.setStep(1);
        strategy.handleNewCandle(event, subscription);

        verify(broker).buyBestPrice("accountId", "instrumentId",70);
    }

    @Test
    void processesCandleAndExecutesSellWhenSignalFallsBelowThreshold() throws AbstractException {
        Candle candle1 = Candle.of(
            Instant.parse("2023-01-01T00:00:00Z"), 2L, 100L, 25
        );
        when(event.getInstrumentUid()).thenReturn("instrumentId");
        when(event.getCandle()).thenReturn(candle1);
        when(candleRepository.findBeforeOrEqual(anyLong(), any(), anyLong())).thenReturn(List.of(candle1));
        when(signalSource.calculate(anyList())).thenReturn(new Signal(-0.6, 1.0));
        when(broker.getPositionSize("accountId", "instrumentId")).thenReturn(100L);

        strategy.setSellThreshold(-0.5);
        strategy.setStep(1);
        strategy.handleNewCandle(event, subscription);

        verify(broker).sellBestPrice("accountId", "instrumentId", 60L);
    }

    @Test
    void doesNotExecuteTradeWhenSignalIsWithinThresholds() {
        NewCandleEvent event1 = new NewCandleEvent(
            "instrumentId",
            Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 2L, 100L, 25)
        );
        NewCandleEvent event2 = new NewCandleEvent(
            "instrumentId",
            Candle.of(Instant.parse("2023-01-01T00:01:00Z"), 2L, 100L, 26)
        );
        when(signalSource.calculate(anyList())).thenReturn(new Signal(0.0, 1.0));

        strategy.setBuyThreshold(0.6);
        strategy.setSellThreshold(-0.5);
        strategy.setStep(1);
        strategy.handleNewCandle(event1, subscription);
        strategy.handleNewCandle(event2, subscription);

        verifyNoInteractions(broker);
    }

    /**
     * Закрывающий сигнал порогов не спрашивает. Уверенность −0.1 слабее порога продажи −0.5, и раньше
     * такой стоп просто не срабатывал - то есть стоп, который не стоп. Пороги существуют, чтобы не
     * согласиться с чужим мнением, а выход не мнение, а распоряжение.
     */
    @Test
    void closesThePositionEvenWhenTheExitIsWeakerThanTheThreshold() throws AbstractException {
        Candle candle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 2L, 100L, 25);

        when(event.getInstrumentUid()).thenReturn("instrumentId");
        when(event.getCandle()).thenReturn(candle);
        when(candleRepository.findBeforeOrEqual(anyLong(), any(), anyLong())).thenReturn(List.of(candle));
        when(signalSource.calculate(anyList()))
            .thenReturn(new Signal(-0.1, 1.0).withType(SignalType.STOP_LOSS));
        when(broker.getPositionSize("accountId", "instrumentId")).thenReturn(100L);

        strategy.setSellThreshold(-0.5);
        strategy.setStep(1);
        strategy.handleNewCandle(event, subscription);

        verify(broker).sellBestPrice("accountId", "instrumentId", 10L);
    }

    /**
     * Сторона закрытия берётся из позиции, а не из знака сигнала: закрыть шорт - купить. Это и есть то,
     * чего источник сигнала знать не может, и из-за чего раньше выход приходилось выражать сроком обёртки.
     */
    @Test
    void buysBackToCloseAShortWhateverTheExitSignSays() throws AbstractException {
        Candle candle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 2L, 100L, 25);

        when(event.getInstrumentUid()).thenReturn("instrumentId");
        when(event.getCandle()).thenReturn(candle);
        when(candleRepository.findBeforeOrEqual(anyLong(), any(), anyLong())).thenReturn(List.of(candle));
        when(signalSource.calculate(anyList()))
            .thenReturn(new Signal(-1.0, 1.0).withType(SignalType.POSITION_EXIT));
        when(broker.getPositionSize("accountId", "instrumentId")).thenReturn(-100L);
        when(broker.getMaxBuyQuantity("accountId", "instrumentId", OrderType.BEST_PRICE))
            .thenReturn(100L);

        strategy.setStep(1);
        strategy.handleNewCandle(event, subscription);

        verify(broker, never()).sellBestPrice(any(), any(), anyLong());
        verify(broker).buyBestPrice(eq("accountId"), eq("instrumentId"), anyLong());
    }

    /** Закрывать нечего - ничего и не делается, сколько бы раз распоряжение ни пришло. */
    @Test
    void doesNothingWhenToldToCloseWithNoPositionHeld() throws AbstractException {
        Candle candle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 2L, 100L, 25);

        when(event.getInstrumentUid()).thenReturn("instrumentId");
        when(event.getCandle()).thenReturn(candle);
        when(candleRepository.findBeforeOrEqual(anyLong(), any(), anyLong())).thenReturn(List.of(candle));
        when(signalSource.calculate(anyList()))
            .thenReturn(new Signal(-1.0, 1.0).withType(SignalType.POSITION_EXIT));
        when(broker.getPositionSize("accountId", "instrumentId")).thenReturn(0L);

        strategy.setStep(1);
        strategy.handleNewCandle(event, subscription);

        verify(broker, never()).sellBestPrice(any(), any(), anyLong());
        verify(broker, never()).buyBestPrice(any(), any(), anyLong());
    }

    /**
     * А вход порог спрашивает по-прежнему, и асимметрия тут сознательная: фильтровать то, что открывает
     * сделку, и не трогать то, что закрывает. В этой базе измерено, что симметричный фильтр, запирающий
     * выход, стоит 51 пункт.
     */
    @Test
    void stillAsksTheThresholdBeforeOpeningAPosition() throws AbstractException {
        Candle candle = Candle.of(Instant.parse("2023-01-01T00:00:00Z"), 2L, 100L, 25);

        when(event.getInstrumentUid()).thenReturn("instrumentId");
        when(event.getCandle()).thenReturn(candle);
        when(candleRepository.findBeforeOrEqual(anyLong(), any(), anyLong())).thenReturn(List.of(candle));
        when(signalSource.calculate(anyList()))
            .thenReturn(new Signal(0.3, 1.0).withType(SignalType.POSITION_ENTRY));

        strategy.setBuyThreshold(0.6);
        strategy.setStep(1);
        strategy.handleNewCandle(event, subscription);

        verify(broker, never()).buyBestPrice(any(), any(), anyLong());
    }
}
