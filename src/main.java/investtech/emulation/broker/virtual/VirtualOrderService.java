package investtech.emulation.broker.virtual;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.exception.ErrorCode;
import investtech.broker.contract.service.exception.ExceptionBuilder;
import investtech.broker.contract.service.exception.InvalidRequestException;
import investtech.broker.contract.service.instrument.request.GetRequest;
import investtech.broker.contract.service.market.request.CandleInterval;
import investtech.broker.contract.service.operation.request.GetPositionsRequest;
import investtech.broker.contract.service.operation.response.PositionSecurities;
import investtech.broker.contract.service.order.OrderServiceInterface;
import investtech.broker.contract.service.order.request.*;
import investtech.broker.contract.service.order.response.*;
import investtech.broker.contract.value.money.Money;
import investtech.broker.contract.value.quatation.Quotation;
import investtech.emulation.Event;
import investtech.emulation.EventInvokerInterface;
import investtech.emulation.EventObserver;
import investtech.emulation.broker.virtual.exception.VirtualBrokerException;
import investtech.emulation.broker.virtual.order.Order;
import investtech.emulation.shared.market.candle.Candle;
import investtech.emulation.shared.market.candle.ComparisonOperator;
import investtech.emulation.shared.market.candle.FindPriceParams;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class VirtualOrderService implements OrderServiceInterface, EventInvokerInterface {
    protected VirtualBroker virtualBroker;
    protected double buyBestPriceRatio = 49;
    protected double sellBestPriceRatio = 51;
    protected double orderCommission = 0.003;
    protected EventObserver eventObserver;
    protected Map<String, Map<String, Order>> orders;
    protected Duration limitOrderLiftTime = Duration.ofDays(1);
    protected Set<String> ordersRequestIds = new HashSet<>();

    public VirtualOrderService(VirtualBroker virtualBroker) {
        this.virtualBroker = virtualBroker;
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());
        assert request.getPrice().isMore(BigDecimal.ZERO) : ExceptionBuilder.create(ErrorCode.INVALID_PARAMETER_PRICE);
        assert request.getQuantity() > 0 : ExceptionBuilder.create(ErrorCode.QUANTITY_MUST_BE_POSITIVE);

        return switch (request.getDirection()) {
            case BUY -> buy(request);
            case SELL -> sell(request);
            case UNSPECIFIED -> throw new InvalidRequestException(ErrorCode.INVALID_PARAMETER_DIRECTION);
        };
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());

        var accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        var cancelOrder = accountOrders.get(request.getOrderId());
        eventObserver.cancel(cancelOrder.getEvent());
        accountOrders.remove(request.getOrderId());

        return new CancelOrderResponse()
                .setTime(virtualBroker.context.getCurrentTime());
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());

        var accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        var accountOrder = accountOrders.get(request.getOrderId());

        if (null != request.getPriceType() && accountOrder.getRequest().getPriceType() != request.getPriceType()) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        return accountOrder.getState();
    }

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());

        var accountOrders = orders
                .computeIfAbsent(request.getAccountId(), k -> new HashMap<>())
                .values()
                .stream()
                .map(Order::getState)
                .collect(Collectors.toList());

        return new GetOrdersResponse().setOrders(accountOrders);
    }

    @Override
    public PostOrderResponse replace(ReplaceOrderRequest request) throws AbstractException {
        assertAccountAvailable(request.getAccountId());

        var accountOrders = orders.getOrDefault(request.getAccountId(), new HashMap<>());

        if (!accountOrders.containsKey(request.getOrderId())) {
            throw ExceptionBuilder.create(ErrorCode.ORDER_NOT_FOUND);
        }

        var order = accountOrders.get(request.getOrderId());
        order
                .setRequestId(request.getIdempotencyKey())
                .setCreatedDate(virtualBroker.context.getCurrentTime())
                .setReportStatus(OrderExecutionReportStatus.NEW);
        order.getRequest()
                .setOrderId(request.getOrderId())
                .setPrice(request.getPrice())
                .setQuantity(request.getQuantity())
                .setPriceType(request.getPriceType());
        var instrument = order.getInstrument();
        var instrumentPrice = calculateCurrentInstrumentPrice(
                instrument.getUid(),
                order.getRequest().getOrderType(),
                order.getRequest().getDirection()
        );
        order.setInitialPrice(instrumentPrice);

        registerOrder(order);

        return toServiceResponse(order);
    }

    @Override
    public void observeEventsBy(EventObserver observer) {
        this.eventObserver = observer;
    }

    @Override
    public void tick() {

    }

    protected PostOrderResponse registerDelayedOrder(Order order) {
        var currentTime = virtualBroker.context.getCurrentTime();
        var request = order.getRequest();

        var buySignalCandle = virtualBroker.marketDataService.findCandlesByClosePrice(
                CandleInterval.HOUR,
                new FindPriceParams()
                        .setFrom(currentTime)
                        .setTo(currentTime.plus(limitOrderLiftTime))
                        .setComparisonOperator(ComparisonOperator.MORE_OR_EQUAL)
                        .setMaxCount(1)
                        .setPrice(request.getPrice())
                        .setInstrumentUid(request.getInstrumentId())
        ).stream().findFirst().orElse(null);

        var lastCandle = virtualBroker.marketDataService.getCandleAt(
                request.getInstrumentId(),
                currentTime.plus(limitOrderLiftTime)
        ).orElse(null);

        if (null == lastCandle) {
            throw new VirtualBrokerException("Emulation error: last candle is null. Market data has ran out");
        }

        Instant eventTime;
        OrderExecutionReportStatus executionReportStatus;

        calculateOrderPrices(order);

        if (null != buySignalCandle) {
            eventTime = buySignalCandle.getTime();
            executionReportStatus = OrderExecutionReportStatus.FILL;
        } else {
            eventTime = lastCandle.getTime();
            executionReportStatus = OrderExecutionReportStatus.REJECTED;
        }

        order
                .setEvent(Event.create(eventTime, this))
                .setReportStatus(executionReportStatus);

        registerOrder(order);
        eventObserver.plan(order.getEvent());

        return toServiceResponse(order);
    }

    protected PostOrderResponse buy(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);

        return OrderType.LIMIT == request.getOrderType()
                && !request.getPrice().isMoreOrEqual(order.getInstrumentPrice())
                ? buyInstrument(order)
                : registerDelayedOrder(order);
    }

    protected PostOrderResponse sellInstrument(Order order) throws AbstractException {
        var operationsService = virtualBroker.operationsService;

        var request = order.getRequest();
        order.setReportStatus(OrderExecutionReportStatus.FILL);

        calculateOrderPrices(order);
        registerOrder(order);

        operationsService.addMoney(
                request.getAccountId(), Money.of(order.getInstrument().getCurrency(), order.getTotalPrice())
        );
        operationsService.subtractPositionBalance(
                request.getAccountId(),
                request.getInstrumentId(),
                request.getQuantity() * order.getInstrument().getLot()
        );

        return toServiceResponse(order);
    }

    protected PostOrderResponse buyInstrument(Order order) throws AbstractException {
        var operationsService = virtualBroker.operationsService;
        order.setReportStatus(OrderExecutionReportStatus.FILL);

        calculateOrderPrices(order);
        registerOrder(order);

        var request = order.getRequest();
        var totalPriceMoney = Money.of(order.getInstrument().getCurrency(), order.getTotalPrice());
        var isEnoughOfMoney = operationsService.isEnoughOfMoney(
                request.getAccountId(),
                totalPriceMoney
        );

        if (isEnoughOfMoney) {
            operationsService.subtractMoney(
                    request.getAccountId(), totalPriceMoney
            );
            operationsService.addPositionBalance(
                    request.getAccountId(),
                    request.getInstrumentId(),
                    request.getQuantity() * order.getInstrument().getLot()
            );
        } else {
            throw ExceptionBuilder.create(ErrorCode.INSUFFICIENT_BALANCE);
        }

        return toServiceResponse(order);
    }

    protected PostOrderResponse sell(PostOrderRequest request) throws AbstractException {
        var order = createOrder(request);

        return OrderType.LIMIT == request.getOrderType()
                && !request.getPrice().isMoreOrEqual(order.getInstrumentPrice())
                ? registerDelayedOrder(order)
                : sellInstrument(order);
    }

    protected Order createOrder(PostOrderRequest request) throws AbstractException {
        var instrumentPosition = getInstrumentPosition(request.getAccountId(), request.getInstrumentId());

        var instrument = virtualBroker
                .instrumentService
                .get(GetRequest.of(instrumentPosition.getInstrumentUid()))
                .getInstrument();

        var instrumentPrice = calculateCurrentInstrumentPrice(
                request.getInstrumentId(),
                request.getOrderType(),
                request.getDirection()
        );

        return new Order()
                .setRequest(request)
                .setCreatedDate(virtualBroker.context.getCurrentTime())
                .setInstrument(instrument)
                .setInstrumentPrice(instrumentPrice);
    }

    protected PositionSecurities getInstrumentPosition(String accountId, String instrumentUid) throws AbstractException {
        var positions = virtualBroker.operationsService.getPositions(GetPositionsRequest.of(accountId));

        return positions
                .getSecurities()
                .stream()
                .filter(position -> Objects.equals(position.getInstrumentUid(), instrumentUid))
                .findFirst()
                .orElseThrow(() -> ExceptionBuilder.create(ErrorCode.INSTRUMENT_NOT_FOUND));
    }

    protected Quotation calculateCurrentInstrumentPrice(String instrumentId, OrderType orderType, OrderDirection orderDirection) {
        var lastCandle = virtualBroker.marketDataService.getInstrumentLastCandle(instrumentId);
        var bestPriceRatio = switch (orderDirection) {
            case BUY -> buyBestPriceRatio;
            case SELL -> sellBestPriceRatio;
            case UNSPECIFIED -> 1;
        };

        return switch (orderType) {
            case UNSPECIFIED, LIMIT, MARKET -> lastCandle.getAveragePrice();
            case BESTPRICE -> calculateBestPrice(lastCandle, bestPriceRatio);
        };
    }

    protected Quotation calculateOrderPrice(long quantity, long lots, Quotation instrumentPrice) {
        return instrumentPrice
                .multiply(BigDecimal.valueOf(lots))
                .multiply(BigDecimal.valueOf(quantity));
    }

    protected Quotation calculateBestPrice(Candle candle, double bestPriceRatio) {
        return candle.getHighPrice()
                .subtract(candle.getLowPrice())
                .multiply(BigDecimal.valueOf(bestPriceRatio))
                .add(candle.getLowPrice());
    }

    protected void assertAccountAvailable(String accountId) {
        var accountState = virtualBroker.userService.getAccountState(accountId);
        assert accountState != null : ExceptionBuilder.create(ErrorCode.ACCOUNT_NOT_FOUND);
        assert !accountState.isBlocked() : ExceptionBuilder.create(ErrorCode.ACCOUNT_BLOCKED);
        assert !accountState.isClosed() : ExceptionBuilder.create(ErrorCode.ACCOUNT_CLOSED);
    }

    protected PostOrderResponse toServiceResponse(Order order) {
        var currency = order.getInstrument().getCurrency();
        var commissionPriceMoney = Money.of(currency, order.getCommissionPrice());

        return new PostOrderResponse()
                .setOrderId(order.getRequest().getOrderId())
                .setDirection(order.getRequest().getDirection())
                .setExecutedOrderPrice(Money.of(currency, order.getExecutedPrice()))
                .setExecutedCommission(commissionPriceMoney)
                .setInitialCommission(commissionPriceMoney)
                .setInstrumentUid(order.getInstrument().getUid())
                .setOrderRequestId(order.getRequestId())
                .setOrderType(order.getRequest().getOrderType())
                .setLotsExecuted(order.getRequest().getQuantity())
                .setLotsRequested(order.getRequest().getQuantity())
                .setTotalOrderAmount(Money.of(currency, order.getTotalPrice()))
                .setInitialOrderPrice(Money.of(currency, order.getInitialPrice()))
                .setExecutionReportStatus(order.getReportStatus());
    }

    protected void calculateOrderPrices(Order order) {
        var instrument = order.getInstrument();
        var instrumentPrice = order.getInstrumentPrice();

        var initialPrice = calculateOrderPrice(order.getRequest().getQuantity(), instrument.getLot(), instrumentPrice);
        var commissionPrice = initialPrice.multiply(Quotation.of(orderCommission));
        var totalPrice = initialPrice.add(commissionPrice);
        var executedPrice = totalPrice.div(order.getRequest().getQuantity() * instrument.getLot());

        order
                .setCommissionPrice(commissionPrice)
                .setInitialPrice(initialPrice)
                .setTotalPrice(totalPrice)
                .setExecutedPrice(executedPrice);
    }

    protected void registerOrder(Order order) {
        if (null == order.getRequestId()) {
            order.setRequestId(UUID.randomUUID().toString());
        } else {
            assert !ordersRequestIds.contains(order.getRequestId()) : ExceptionBuilder.create(ErrorCode.DUPLICATE_ORDER);
        }

        if (null == order.getRequest().getOrderId()) {
            order.getRequest().setOrderId(UUID.randomUUID().toString());
        }

        ordersRequestIds.add(order.getRequestId());
        orders
                .computeIfAbsent(order.getRequest().getAccountId(), x -> new HashMap<>())
                .put(order.getRequest().getOrderId(), order);
    }
}
