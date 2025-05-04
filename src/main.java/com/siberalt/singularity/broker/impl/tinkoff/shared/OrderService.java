package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.OrderServiceInterface;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.CancelOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.response.GetOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.response.OrderState;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.*;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.core.OrdersService;

public class OrderService implements OrderServiceInterface {
    protected OrdersService ordersServiceApi;

    public OrderService(OrdersService ordersServiceApi) {
        this.ordersServiceApi = ordersServiceApi;
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        var price = null == request.getPrice()
                ? Quotation.newBuilder().build()
                : QuotationTranslator.toTinkoff(request.getPrice());

        var response = ExceptionConverter.rethrowContractExceptionOnError(
                () -> ordersServiceApi.postOrderSync(
                        request.getInstrumentId(),
                        request.getQuantity(),
                        price,
                        OrderDirectionTranslator.toTinkoff(request.getDirection()),
                        request.getAccountId(),
                        OrderTypeTranslator.toTinkoff(request.getOrderType()),
                        request.getIdempotencyKey()
                )
        );

        return toContractPostOrderResponse(response);
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
                () -> ordersServiceApi.cancelOrderSync(
                        request.getAccountId(),
                        request.getOrderId()
                )
        );

        return new CancelOrderResponse().setTime(response);
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
                () -> ordersServiceApi.getOrderStateSync(request.getAccountId(), request.getOrderId())
        );

        return new OrderState()
                .setOrderId(response.getOrderId())
                .setExecutionStatus(OrderExecutionReportStatusTranslator.toContract(response.getExecutionReportStatus()))
                .setLotsRequested(response.getLotsRequested())
                .setLotsExecuted(response.getLotsExecuted())
                .setInitialOrderPrice(MoneyValueTranslator.toContract(response.getInitialOrderPrice()))
                .setExecutedOrderPrice(MoneyValueTranslator.toContract(response.getExecutedOrderPrice()))
                .setTotalPrice(MoneyValueTranslator.toContract(response.getTotalOrderAmount()))
                .setAveragePositionPrice(MoneyValueTranslator.toContract(response.getAveragePositionPrice()))
                .setInitialCommission(MoneyValueTranslator.toContract(response.getInitialCommission()))
                .setExecutedCommission(MoneyValueTranslator.toContract(response.getExecutedCommission()))
                .setDirection(OrderDirectionTranslator.toContract(response.getDirection()))
                .setInitialSecurityPrice(MoneyValueTranslator.toContract(response.getInitialSecurityPrice()))
                .setStages(ListTranslator.translate(response.getStagesList(), OrderStageTranslator::toContract))
                .setServiceCommission(MoneyValueTranslator.toContract(response.getServiceCommission()))
                .setCurrency(response.getCurrency())
                .setOrderType(OrderTypeTranslator.toContract(response.getOrderType()))
                .setOrderDate(TimestampTranslator.toContract(response.getOrderDate()))
                .setInstrumentUid(response.getInstrumentUid())
                .setIdempotencyKey(response.getOrderRequestId());
    }

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
                () -> ordersServiceApi.getOrdersSync(request.getAccountId())
        );

        return new GetOrdersResponse()
                .setOrders(ListTranslator.translate(response, OrderStateTranslator::toContract));
    }

    public PostOrderResponse replace(ReplaceOrderRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
                () -> ordersServiceApi.replaceOrderSync(
                        request.getAccountId(),
                        request.getQuantity(),
                        QuotationTranslator.toTinkoff(request.getPrice()),
                        request.getIdempotencyKey(),
                        request.getOrderId(),
                        PriceTypeTranslator.toTinkoff(request.getPriceType())
                )
        );

        return toContractPostOrderResponse(response);
    }

    protected PostOrderResponse toContractPostOrderResponse(ru.tinkoff.piapi.contract.v1.PostOrderResponse response) {
        return new PostOrderResponse()
                .setOrderId(response.getOrderId())
                .setExecutionStatus(OrderExecutionReportStatusTranslator.toContract(response.getExecutionReportStatus()))
                .setLotsRequested(response.getLotsRequested())
                .setLotsExecuted(response.getLotsExecuted())
                .setInitialPrice(MoneyValueTranslator.toContract(response.getInitialOrderPrice()))
                .setTotalPricePerOne(MoneyValueTranslator.toContract(response.getExecutedOrderPrice()))
                .setTotalPrice(MoneyValueTranslator.toContract(response.getTotalOrderAmount()))
                .setInitialCommission(MoneyValueTranslator.toContract(response.getInitialCommission()))
                .setExecutedCommission(MoneyValueTranslator.toContract(response.getExecutedCommission()))
                .setAciValue(MoneyValueTranslator.toContract(response.getAciValue()))
                .setDirection(OrderDirectionTranslator.toContract(response.getDirection()))
                .setInitialPricePerOne(MoneyValueTranslator.toContract(response.getInitialSecurityPrice()))
                .setOrderType(OrderTypeTranslator.toContract(response.getOrderType()))
                .setMessage(response.getMessage())
                .setInstrumentUid(response.getInstrumentUid())
                .setIdempotencyKey(response.getOrderRequestId());
    }
}
