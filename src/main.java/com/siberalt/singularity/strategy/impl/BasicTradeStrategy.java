package com.siberalt.singularity.strategy.impl;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.shared.EventSubscriptionBrokerFacade;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;
import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.observer.Observer;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BasicTradeStrategy implements StrategyInterface {
    private final EventSubscriptionBrokerFacade broker;
    private final String instrumentId;
    private final String accountId;
    private final UpsideCalculator upsideCalculator;
    private final ReadCandleRepository candleRepository;
    private long lookbackCandles = 24 * 60;
    private double buyThreshold = 0.7; // Example threshold for trading decision
    private double sellThreshold = -0.5; // Example threshold for trading decision
    private boolean isInitialized = false;
    private int step = 5; // Process every 'step' candles
    private List<Candle> lastCandles;

    public BasicTradeStrategy(
        EventSubscriptionBrokerFacade broker,
        String instrumentId,
        String accountId,
        UpsideCalculator upsideCalculator,
        ReadCandleRepository candleRepository
    ) {
        this.broker = broker;
        this.instrumentId = instrumentId;
        this.accountId = accountId;
        this.upsideCalculator = upsideCalculator;
        this.candleRepository = candleRepository;
    }

    public BasicTradeStrategy(
        EventSubscriptionBroker broker,
        String instrumentId,
        String accountId,
        UpsideCalculator upsideCalculator,
        ReadCandleRepository candleRepository
    ) {
        this.broker = EventSubscriptionBrokerFacade.of(broker);
        this.instrumentId = instrumentId;
        this.accountId = accountId;
        this.upsideCalculator = upsideCalculator;
        this.candleRepository = candleRepository;
    }

    public BasicTradeStrategy setLookbackCandles(long lookbackCandles) {
        this.lookbackCandles = lookbackCandles;
        return this;
    }

    public BasicTradeStrategy setBuyThreshold(double buyThreshold) {
        this.buyThreshold = buyThreshold;
        return this;
    }

    public BasicTradeStrategy setSellThreshold(double sellThreshold) {
        this.sellThreshold = sellThreshold;
        return this;
    }

    public BasicTradeStrategy setStep(int step) {
        this.step = step;
        return this;
    }

    @Override
    public void run(Observer observer) {
        SubscriptionSpec<NewCandleEvent> subscriptionSpec = new NewCandleSubscriptionSpec(Set.of(instrumentId));
        broker.subscribe(subscriptionSpec, this::handleNewCandle);
    }

    public void handleNewCandle(NewCandleEvent event, Subscription subscription) {
        if (!event.getCandle().getInstrumentUid().equals(instrumentId)) {
            return;
        }

        if (!isInitialized) {
            // Initial setup if needed
            isInitialized = true;
            lastCandles = new ArrayList<>(
                candleRepository.findBeforeOrEqual(
                    instrumentId,
                    event.getCandle().getTime(),
                    lookbackCandles
                ));
        } else {
            lastCandles.add(event.getCandle());
        }

        if (!lastCandles.isEmpty() && lastCandles.size() % step == 0) {
            var upside = upsideCalculator.calculate(lastCandles);
            lastCandles.clear();

            try {
                if (upside.signal() >= buyThreshold) {
                    broker.buyBestPriceFullBalance(accountId, instrumentId);
                } else if (upside.signal() <= sellThreshold) {
                    long positionSize = broker.getPositionSize(accountId, instrumentId);

                    if (positionSize > 0) {
                        broker.sellBestPrice(accountId, instrumentId, positionSize);
                    }
                }
            } catch (AbstractException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
