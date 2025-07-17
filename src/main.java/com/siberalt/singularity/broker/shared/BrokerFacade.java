package com.siberalt.singularity.broker.shared;

import com.siberalt.singularity.broker.contract.execution.Broker;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.contract.service.operation.response.Position;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.CancelOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.response.GetOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.shared.dto.BuyRequest;

public class BrokerFacade {
    protected Broker broker;
    protected OrderCalculationService orderCalculationService = new OrderCalculationService();

    public BrokerFacade(Broker broker) {
        this.broker = broker;
    }

    public long getPositionSize(String accountId, String instrumentId) throws AbstractException {
        return broker.getOperationsService().getPositions(GetPositionsRequest.of(accountId))
            .getSecurities().stream()
            .filter(position -> position.getInstrumentUid().equals(instrumentId))
            .mapToLong(Position::getBalance)
            .findFirst()
            .orElse(0L);
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

    public PostOrderResponse sellAllLimit(String accountId, String instrumentId, double price) throws AbstractException {
        return broker.getOrderService().post(new PostOrderRequest()
            .setAccountId(accountId)
            .setInstrumentId(instrumentId)
            .setQuantity(getPositionSize(accountId, instrumentId))
            .setPrice(Quotation.of(price))
            .setOrderType(OrderType.LIMIT)
            .setDirection(OrderDirection.SELL));
    }

    public PostOrderResponse sellAllMarket(String accountId, String instrumentId) throws AbstractException {
        return broker.getOrderService().post(new PostOrderRequest()
            .setAccountId(accountId)
            .setInstrumentId(instrumentId)
            .setQuantity(getPositionSize(accountId, instrumentId))
            .setOrderType(OrderType.MARKET)
            .setDirection(OrderDirection.SELL));
    }

    public PostOrderResponse sellAllBestPrice(String accountId, String instrumentId) throws AbstractException {
        return broker.getOrderService().post(new PostOrderRequest()
            .setAccountId(accountId)
            .setInstrumentId(instrumentId)
            .setQuantity(getPositionSize(accountId, instrumentId))
            .setOrderType(OrderType.BEST_PRICE)
            .setDirection(OrderDirection.SELL));
    }

    public PostOrderResponse sellMarket(String accountId, String instrumentId, long amount) throws AbstractException {
        return broker.getOrderService().post(
            new PostOrderRequest()
                .setAccountId(accountId)
                .setInstrumentId(instrumentId)
                .setQuantity(amount)
                .setOrderType(OrderType.MARKET)
                .setDirection(OrderDirection.SELL)
        );
    }

    public PostOrderResponse sellLimit(String accountId, String instrumentId, long amount, double price) throws AbstractException {
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

    public PostOrderResponse buyMarketFullBalance(String accountId, String instrumentId) throws AbstractException {
        long possibleBuyQuantity = orderCalculationService.calculatePossibleBuyQuantity(broker, new BuyRequest(
            accountId,
            instrumentId,
            OrderType.MARKET
        ));

        return broker.getOrderService().post(new PostOrderRequest()
            .setAccountId(accountId)
            .setInstrumentId(instrumentId)
            .setQuantity(possibleBuyQuantity)
            .setOrderType(OrderType.MARKET)
            .setDirection(OrderDirection.BUY));
    }

    public PostOrderResponse buyLimitFullBalance(String accountId, String instrumentId, double price) throws AbstractException {
        long possibleBuyQuantity = orderCalculationService.calculatePossibleBuyQuantity(broker, new BuyRequest(
            accountId,
            instrumentId,
            OrderType.LIMIT
        ));

        return broker.getOrderService().post(new PostOrderRequest()
            .setAccountId(accountId)
            .setInstrumentId(instrumentId)
            .setPrice(Quotation.of(price))
            .setQuantity(possibleBuyQuantity)
            .setOrderType(OrderType.LIMIT)
            .setDirection(OrderDirection.BUY));
    }

    public PostOrderResponse buyBestPriceFullBalance(String accountId, String instrumentId) throws AbstractException {
        long possibleBuyQuantity = orderCalculationService.calculatePossibleBuyQuantity(broker, new BuyRequest(
            accountId,
            instrumentId,
            OrderType.BEST_PRICE
        ));

        return broker.getOrderService().post(new PostOrderRequest()
            .setAccountId(accountId)
            .setInstrumentId(instrumentId)
            .setQuantity(possibleBuyQuantity)
            .setOrderType(OrderType.BEST_PRICE)
            .setDirection(OrderDirection.BUY));
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

    public static BrokerFacade of(Broker broker) {
        return new BrokerFacade(broker);
    }
}