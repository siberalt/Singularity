package com.siberalt.singularity.broker.shared;

import com.siberalt.singularity.broker.contract.execution.Broker;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.request.GetCurrentPriceRequest;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.entity.position.Position;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.CancelOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.response.GetOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.shared.dto.BuyRequest;

import java.util.Collection;

public class BrokerFacade {
    protected Broker broker;
    protected OrderCalculationService orderCalculationService = new OrderCalculationService();

    public BrokerFacade(Broker broker) {
        this.broker = broker;
    }

    /**
     * The account's free money in one currency, or zero when it holds none of it. Read through
     * {@code getPositions}, which is the only place the {@link Broker} contract exposes balances -
     * a broker-specific service may offer a direct accessor, but nothing portable can rely on it.
     */
    public Money getAvailableMoney(String accountId, String currencyIso) throws AbstractException {
        return broker.getOperationsService().getPositions(GetPositionsRequest.of(accountId))
            .getMoney().stream()
            .filter(money -> currencyIso.equals(money.getCurrencyIso()))
            .findFirst()
            .orElseGet(() -> Money.of(currencyIso, Quotation.ZERO));
    }

    /**
     * Money set aside for orders that are still working, which is the account's just as much as the
     * free balance is - it is only spoken for. Valuing an account without it makes every pending
     * order look like a loss.
     */
    public Money getBlockedMoney(String accountId, String currencyIso) throws AbstractException {
        Collection<Money> blocked = broker.getOperationsService()
            .getPositions(GetPositionsRequest.of(accountId))
            .getBlocked();

        if (blocked == null) {
            return Money.of(currencyIso, Quotation.ZERO);
        }

        return blocked.stream()
            .filter(money -> currencyIso.equals(money.getCurrencyIso()))
            .findFirst()
            .orElseGet(() -> Money.of(currencyIso, Quotation.ZERO));
    }

    /**
     * Everything the account holds of this instrument - the free lots and the ones reserved for a
     * sell that has not finished. {@link #getPositionSize} counts only what is free, because that is
     * what another order could be placed against; this counts what is owned.
     */
    public long getHeldPositionSize(String accountId, String instrumentId) throws AbstractException {
        return broker.getOperationsService().getPositions(GetPositionsRequest.of(accountId))
            .getSecurities().stream()
            .filter(position -> position.getInstrumentUid().equals(instrumentId))
            .mapToLong(position -> position.getBalance() + position.getBlocked())
            .findFirst()
            .orElse(0L);
    }

    public Quotation getLastPrice(String instrumentId) throws AbstractException {
        return broker.getMarketDataService()
            .getCurrentPrice(new GetCurrentPriceRequest(instrumentId))
            .getPrice();
    }

    public long getPositionSize(String accountId, String instrumentId) throws AbstractException {
        return broker.getOperationsService().getPositions(GetPositionsRequest.of(accountId))
            .getSecurities().stream()
            .filter(position -> position.getInstrumentUid().equals(instrumentId))
            .mapToLong(Position::getBalance)
            .findFirst()
            .orElse(0L);
    }

    public long closePositionUnchecked(String accountId, String instrumentId) {
        try {
            return closePosition(accountId, instrumentId);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    public long closePosition(String accountId, String instrumentId) throws AbstractException {
        long instrumentCount = this.getPositionSize(accountId, instrumentId);

        if (instrumentCount > 0) {
            this.sellMarket(accountId, instrumentId, instrumentCount);
        } else if (instrumentCount < 0) {
            this.buyMarket(accountId, instrumentId, Math.abs(instrumentCount));
        }

        return instrumentCount;
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

    public PostOrderResponse sellBestPrice(String accountId, String instrumentId, long amount) throws AbstractException {
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
        long possibleBuyQuantity = getMaxBuyQuantity(accountId, instrumentId);

        return broker.getOrderService().post(new PostOrderRequest()
            .setAccountId(accountId)
            .setInstrumentId(instrumentId)
            .setQuantity(possibleBuyQuantity)
            .setOrderType(OrderType.MARKET)
            .setDirection(OrderDirection.BUY));
    }

    public long getMaxBuyQuantity(String accountId, String instrumentId) throws AbstractException {
        return getMaxBuyQuantity(accountId, instrumentId, OrderType.MARKET);
    }

    /**
     * The most the account could buy, priced as the kind of order that will be sent. Ask for one
     * type and post another and the answer is off by whatever the two types cost differently.
     */
    public long getMaxBuyQuantity(String accountId, String instrumentId, OrderType orderType)
        throws AbstractException {
        return orderCalculationService.calculateMaxBuyQuantity(
            broker,
            new BuyRequest(accountId, instrumentId, orderType)
        );
    }

    public long buyFullBalanceUnchecked(String accountId, String instrumentId) {
        try {
            return buyFullBalance(accountId, instrumentId);
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }

    public long buyFullBalance(String accountId, String instrumentId) throws AbstractException {
        return buyFullBalance(accountId, instrumentId, OrderType.MARKET);
    }

    public long buyFullBalance(String accountId, String instrumentId, OrderType orderType)
        throws AbstractException {
        long possibleBuyQuantity = getMaxBuyQuantity(accountId, instrumentId, orderType);

        if (possibleBuyQuantity <= 0) {
            return possibleBuyQuantity;
        }

        broker.getOrderService().post(new PostOrderRequest()
            .setAccountId(accountId)
            .setInstrumentId(instrumentId)
            .setQuantity(possibleBuyQuantity)
            .setOrderType(orderType)
            .setDirection(OrderDirection.BUY));

        return possibleBuyQuantity;
    }

    public long buyBestPriceFullBalance(String accountId, String instrumentId) throws AbstractException {
        return buyFullBalance(accountId, instrumentId, OrderType.BEST_PRICE);
    }

    public PostOrderResponse buyMarket(String accountId, String instrumentId, long amount) throws AbstractException {
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

    public PostOrderResponse buyBestPrice(String accountId, String instrumentId, long amount) throws AbstractException {
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

    public PostOrderResponse sellBestPriceUnchecked(String accountId, String instrumentId, long amount) {
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