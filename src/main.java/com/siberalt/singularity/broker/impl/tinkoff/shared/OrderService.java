package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.GetMaxLotsOrderService;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.request.CancelOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetMaxLotsRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetOrderStateRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetOrdersRequest;
import com.siberalt.singularity.broker.contract.service.order.request.PostOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.response.*;
import com.siberalt.singularity.broker.contract.service.order.response.CancelOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.response.GetMaxLotsResponse;
import com.siberalt.singularity.broker.contract.service.order.response.GetOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.response.OrderState;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.*;
import com.siberalt.singularity.broker.shared.ListTranslator;
import ru.tinkoff.piapi.contract.v1.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class OrderService implements com.siberalt.singularity.broker.contract.service.order.OrderService, GetMaxLotsOrderService {
    private final OrdersServiceGrpc.OrdersServiceBlockingStub ordersServiceApi;
    private final MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataServiceApi;

    public OrderService(OrdersServiceGrpc.OrdersServiceBlockingStub ordersServiceApi, MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataServiceStub) {
        this.ordersServiceApi = ordersServiceApi;
        this.marketDataServiceApi = marketDataServiceStub;
    }

    @Override
    public GetPriceResponse getPrice(GetPriceRequest request) throws AbstractException {
        PostOrderRequest postOrderRequest = request.getPostOrderRequest();

        if (postOrderRequest.getPrice() == null
            || postOrderRequest.getPrice().equals(com.siberalt.singularity.broker.contract.value.quotation.Quotation.ZERO)) {
            Instant to = Instant.now();
            Instant from = to.minus(5, ChronoUnit.MINUTES);

            var candles = marketDataServiceApi.getCandles(
                GetCandlesRequest.newBuilder()
                    .setFrom(TimestampTranslator.toTinkoff(from))
                    .setTo(TimestampTranslator.toTinkoff(to))
                    .setInstrumentId(request.getPostOrderRequest().getInstrumentId())
                    .setLimit(5)
                    .setInterval(CandleInterval.CANDLE_INTERVAL_1_MIN)
                    .build()
            );

            if (!candles.getCandlesList().isEmpty()) {
                var closePrice = candles.getCandlesList().getLast().getClose();
                postOrderRequest.setPrice(QuotationTranslator.toContract(closePrice));
            }
        }

        var response = ExceptionConverter.rethrowContractExceptionOnError(() -> ordersServiceApi.getOrderPrice(
                GetOrderPriceRequest.newBuilder()
                    .setPrice(QuotationTranslator.toTinkoff(postOrderRequest.getPrice()))
                    .setAccountId(postOrderRequest.getAccountId())
                    .setQuantity(postOrderRequest.getQuantity())
                    .setDirection(OrderDirectionTranslator.toTinkoff(postOrderRequest.getDirection()))
                    .setInstrumentId(postOrderRequest.getInstrumentId())
                    .build()
            )
        );

        return new GetPriceResponse(
            MoneyValueTranslator.toContract(response.getTotalOrderAmount()).getQuotation(),
            MoneyValueTranslator.toContract(response.getExecutedCommission()).getQuotation()
        );
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        var price = request.getPrice() == null ? Quotation.newBuilder().build() : QuotationTranslator.toTinkoff(request.getPrice());
        var response = ExceptionConverter.rethrowContractExceptionOnError(() -> ordersServiceApi.postOrder(
            ru.tinkoff.piapi.contract.v1.PostOrderRequest.newBuilder()
                .setInstrumentId(request.getInstrumentId())
                .setQuantity(request.getQuantity())
                .setPrice(price)
                .setDirection(OrderDirectionTranslator.toTinkoff(request.getDirection()))
                .setAccountId(request.getAccountId())
                .setOrderType(OrderTypeTranslator.toTinkoff(request.getOrderType()))
                .build()
        ));
        return toContractPostOrderResponse(response);
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() -> ordersServiceApi.cancelOrder(
            ru.tinkoff.piapi.contract.v1.CancelOrderRequest.newBuilder()
                .setOrderId(request.getOrderId())
                .setAccountId(request.getAccountId())
                .build()

        ));
        return new CancelOrderResponse().setTime(TimestampTranslator.toContract(response.getTime()));
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() -> ordersServiceApi.getOrderState(
            ru.tinkoff.piapi.contract.v1.GetOrderStateRequest.newBuilder()
                .setAccountId(request.getAccountId())
                .setOrderId(request.getOrderId())
                .build()
        ));
        return OrderStateTranslator.toContract(response);
    }

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
            () -> ordersServiceApi.getOrders(ru.tinkoff.piapi.contract.v1.GetOrdersRequest.newBuilder()
                .setAccountId(request.getAccountId())
                .build())
        );

        return new GetOrdersResponse().setOrders(ListTranslator.translate(response.getOrdersList(), OrderStateTranslator::toContract));
    }

    @Override
    public GetMaxLotsResponse getMaxLots(GetMaxLotsRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
            () -> ordersServiceApi.getMaxLots(
                ru.tinkoff.piapi.contract.v1.GetMaxLotsRequest.newBuilder()
                    .setAccountId(request.accountId())
                    .setInstrumentId(request.instrumentId())
                    .setPrice(QuotationTranslator.toTinkoff(request.price()))
                    .build()
            )
        );

        return GetMaxLotsResponse.builder()
            .sellLimits(new GetMaxLotsResponse.SellLimits(response.getSellLimits().getSellMaxLots()))
            .sellMarginLimits(new GetMaxLotsResponse.SellLimits(response.getSellMarginLimits().getSellMaxLots()))
            .buyMarginLimits(BuyLimitsTranslator.toContract(response.getBuyMarginLimits()))
            .buyLimits(BuyLimitsTranslator.toContract(response.getBuyLimits()))
            .currency(response.getCurrency())
            .build();
    }

    protected PostOrderResponse toContractPostOrderResponse(
        ru.tinkoff.piapi.contract.v1.PostOrderResponse response
    ) {
        return new PostOrderResponse()
            .setOrderId(response.getOrderId())
            .setExecutionStatus(OrderExecutionReportStatusTranslator.toContract(response.getExecutionReportStatus()))
            .setLotsRequested(response.getLotsRequested())
            .setLotsExecuted(response.getLotsExecuted())
            .setTotalBalanceChange(MoneyValueTranslator.toContract(response.getTotalOrderAmount()))
            .setExecutedCommission(MoneyValueTranslator.toContract(response.getExecutedCommission()))
            .setInitialCommission(MoneyValueTranslator.toContract(response.getInitialCommission()))
            .setAciValue(MoneyValueTranslator.toContract(response.getAciValue()))
            .setDirection(OrderDirectionTranslator.toContract(response.getDirection()))
            .setInstrumentPrice(MoneyValueTranslator.toContract(response.getInitialSecurityPrice()))
            .setOrderType(OrderTypeTranslator.toContract(response.getOrderType()))
            .setMessage(response.getMessage())
            .setInstrumentUid(response.getInstrumentUid())
            .setIdempotencyKey(response.getOrderRequestId());
    }
}
