package com.siberalt.singularity.strategy.impl;

import com.siberalt.singularity.broker.contract.execution.EventSubscriptionBroker;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.broker.contract.service.event.dispatcher.subscriptions.NewCandleSubscriptionSpec;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.shared.EventSubscriptionBrokerFacade;
import com.siberalt.singularity.event.subscription.Subscription;
import com.siberalt.singularity.event.subscription.SubscriptionSpec;
import com.siberalt.singularity.strategy.StrategyInterface;
import com.siberalt.singularity.strategy.observer.Observer;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

public class ProfitTakerStrategy implements StrategyInterface {
    private final String instrumentId;
    private final String accountId;
    private final double targetProfitPercentage;
    private double targetLossPercentage;
    private Quotation maxPrice;
    private int priceHistoryLimit = 10;
    private final Deque<Quotation> priceHistory = new ArrayDeque<>();
    private final EventSubscriptionBrokerFacade broker;
    private double sellOutRatio = 1;

    public ProfitTakerStrategy(
        String instrumentId,
        String accountId,
        double targetProfitPercentage,
        double targetLossPercentage,
        EventSubscriptionBroker broker
    ) {
        this.instrumentId = instrumentId;
        this.accountId = accountId;
        this.targetProfitPercentage = targetProfitPercentage;
        this.targetLossPercentage = targetLossPercentage;
        this.broker = new EventSubscriptionBrokerFacade(broker);
    }

    public ProfitTakerStrategy(
        String instrumentId,
        String accountId,
        double targetProfitPercentage,
        double targetLossPercentage,
        EventSubscriptionBroker broker,
        Quotation maxPrice
    ) {
        this.instrumentId = instrumentId;
        this.accountId = accountId;
        this.targetProfitPercentage = targetProfitPercentage;
        this.targetLossPercentage = targetLossPercentage;
        this.broker = new EventSubscriptionBrokerFacade(broker);
        this.maxPrice = maxPrice;
    }

    public ProfitTakerStrategy(
        String instrumentId,
        String accountId,
        double targetProfitPercentage,
        EventSubscriptionBroker broker,
        Quotation maxPrice
    ) {
        this.instrumentId = instrumentId;
        this.accountId = accountId;
        this.targetProfitPercentage = targetProfitPercentage;
        this.broker = new EventSubscriptionBrokerFacade(broker);
        this.maxPrice = maxPrice;
    }

    public ProfitTakerStrategy setSellOutRatio(double sellOutRatio) {
        this.sellOutRatio = sellOutRatio;
        return this;
    }

    public void setPriceHistoryLimit(int priceHistoryLimit) {
        this.priceHistoryLimit = priceHistoryLimit;
    }

    @Override
    public void run(Observer observer) {
        SubscriptionSpec<NewCandleEvent> subscriptionSpec = new NewCandleSubscriptionSpec(Set.of(instrumentId));
        broker.subscribe(subscriptionSpec, this::onNewCandle);
    }

    private void onNewCandle(NewCandleEvent event, Subscription subscription) {
        Quotation currentPrice = event.getCandle().getClosePrice();
        priceHistory.addLast(currentPrice);

        if (priceHistory.size() > priceHistoryLimit) {
            priceHistory.removeFirst();
        }

        Quotation averagePrice = priceHistory.stream()
            .reduce(Quotation.ZERO, Quotation::add)
            .divide(priceHistoryLimit);

        if (maxPrice == null || averagePrice.isGreaterThan(maxPrice)) {
            maxPrice = averagePrice;
        } else {
            Quotation difference = maxPrice.subtract(averagePrice);
            BigDecimal profitPercentage = difference.divide(maxPrice).toBigDecimal();

            try {
                if (profitPercentage.doubleValue() >= targetProfitPercentage) {
                    sellPosition(accountId, instrumentId, subscription);
                } else if (profitPercentage.doubleValue() <= -targetLossPercentage) {
                    sellPosition(accountId, instrumentId, subscription);
                }

            } catch (Exception e) {
                e.printStackTrace(); // Handle exceptions appropriately in production code
            }
        }
    }

    private void sellPosition(
        String accountId,
        String instrumentId,
        Subscription subscription
    ) throws AbstractException {
        // If the price has dropped below the target loss percentage, sell the position
        subscription.stop();
        long positionSize = broker.getPositionSize(accountId, instrumentId);
        int sellPositionSize = (int) (positionSize * sellOutRatio);
        PostOrderResponse response = broker.sellMarket(accountId, instrumentId, sellPositionSize);
        System.out.println("Price peak reached: " + maxPrice);
        System.out.println("Position sold: " + instrumentId + ", Size: " + positionSize + ", Sell Size: " + sellPositionSize + ", Response: " + response);
        maxPrice = null; // Reset max price after taking loss
    }
}
