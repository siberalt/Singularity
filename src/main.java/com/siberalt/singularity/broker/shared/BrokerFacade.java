package com.siberalt.singularity.broker.shared;

import com.siberalt.singularity.broker.contract.execution.Broker;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.CancelOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.response.GetOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

public class BrokerFacade {
    protected Broker broker;

    public BrokerFacade(Broker broker) {
        this.broker = broker;
    }

    public GetOrdersResponse getOrders(String accountId) throws AbstractException {
        return broker.getOrderService().get(
            new GetOrdersRequest()
                .setAccountId(accountId)
        );
    }

    public CancelOrderResponse cancelOrder(String accountId, String orderId) throws AbstractException {
        return broker.getOrderService().cancel(
            new CancelOrderRequest()
                .setAccountId(accountId)
                .setOrderId(orderId)
        );
    }

    public PostOrderResponse sellMarket(String accountId, String instrumentId, int amount) throws AbstractException {
        return broker.getOrderService().post(
            new PostOrderRequest()
                .setAccountId(accountId)
                .setInstrumentId(instrumentId)
                .setQuantity(amount)
                .setOrderType(OrderType.MARKET)
                .setDirection(OrderDirection.SELL)
        );
    }

    public PostOrderResponse sellLimit(String accountId, String instrumentId, int amount, double price) throws AbstractException {
        return broker.getOrderService().post(
            new PostOrderRequest()
                .setAccountId(accountId)
                .setInstrumentId(instrumentId)
                .setQuantity(amount)
                .setPrice(Quotation.of(price))
                .setOrderType(OrderType.LIMIT)
                .setDirection(OrderDirection.SELL)
        );
    }

    public PostOrderResponse sellBestPrice(String accountId, String instrumentId, int amount) throws AbstractException {
        return broker.getOrderService().post(
            new PostOrderRequest()
                .setAccountId(accountId)
                .setInstrumentId(instrumentId)
                .setQuantity(amount)
                .setOrderType(OrderType.BEST_PRICE)
                .setDirection(OrderDirection.SELL)
        );
    }

    public PostOrderResponse buyMarket(String accountId, String instrumentId, int amount) throws AbstractException {
        return broker.getOrderService().post(
            new PostOrderRequest()
                .setAccountId(accountId)
                .setInstrumentId(instrumentId)
                .setQuantity(amount)
                .setOrderType(OrderType.MARKET)
                .setDirection(OrderDirection.BUY)
        );
    }

    public PostOrderResponse buyLimit(String accountId, String instrumentId, int amount, double price) throws AbstractException {
        return broker.getOrderService().post(
            new PostOrderRequest()
                .setAccountId(accountId)
                .setInstrumentId(instrumentId)
                .setQuantity(amount)
                .setPrice(Quotation.of(price))
                .setOrderType(OrderType.LIMIT)
                .setDirection(OrderDirection.BUY)
        );
    }

    public PostOrderResponse buyBestPrice(String accountId, String instrumentId, int amount) throws AbstractException {
        return broker.getOrderService().post(
            new PostOrderRequest()
                .setAccountId(accountId)
                .setInstrumentId(instrumentId)
                .setQuantity(amount)
                .setOrderType(OrderType.BEST_PRICE)
                .setDirection(OrderDirection.BUY)
        );
    }

    // Unchecked methods
    public GetOrdersResponse getOrdersUnchecked(String accountId) {
        try {
            return getOrders(accountId);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    public CancelOrderResponse cancelOrderUnchecked(String accountId, String orderId) {
        try {
            return cancelOrder(accountId, orderId);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    public PostOrderResponse sellMarketUnchecked(String accountId, String instrumentId, int amount) {
        try {
            return sellMarket(accountId, instrumentId, amount);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    public PostOrderResponse sellLimitUnchecked(String accountId, String instrumentId, int amount, double price) {
        try {
            return sellLimit(accountId, instrumentId, amount, price);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    public PostOrderResponse sellBestPriceUnchecked(String accountId, String instrumentId, int amount) {
        try {
            return sellBestPrice(accountId, instrumentId, amount);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    public PostOrderResponse buyMarketUnchecked(String accountId, String instrumentId, int amount) {
        try {
            return buyMarket(accountId, instrumentId, amount);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    public PostOrderResponse buyLimitUnchecked(String accountId, String instrumentId, int amount, double price) {
        try {
            return buyLimit(accountId, instrumentId, amount, price);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    public PostOrderResponse buyBestPriceUnchecked(String accountId, String instrumentId, int amount) {
        try {
            return buyBestPrice(accountId, instrumentId, amount);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }
}