package investtech.broker.impl.tinkoff.shared;

import investtech.broker.common.ListTranslator;
import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.order.OrderServiceInterface;
import investtech.broker.contract.service.order.request.*;
import investtech.broker.contract.service.order.response.CancelOrderResponse;
import investtech.broker.contract.service.order.response.GetOrdersResponse;
import investtech.broker.contract.service.order.response.OrderState;
import investtech.broker.contract.service.order.response.PostOrderResponse;
import investtech.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import investtech.broker.impl.tinkoff.shared.translation.*;
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
                        request.getOrderId()
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
                .setExecutionReportStatus(OrderExecutionReportStatusTranslator.toContract(response.getExecutionReportStatus()))
                .setLotsRequested(response.getLotsRequested())
                .setLotsExecuted(response.getLotsExecuted())
                .setInitialOrderPrice(MoneyValueTranslator.toContract(response.getInitialOrderPrice()))
                .setExecutedOrderPrice(MoneyValueTranslator.toContract(response.getExecutedOrderPrice()))
                .setTotalOrderAmount(MoneyValueTranslator.toContract(response.getTotalOrderAmount()))
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
                .setOrderRequestId(response.getOrderRequestId());
    }

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
                () -> ordersServiceApi.getOrdersSync(request.getAccountId())
        );

        return new GetOrdersResponse()
                .setOrders(ListTranslator.translate(response, OrderStateTranslator::toContract));
    }

    @Override
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
                .setInitialOrderPrice(MoneyValueTranslator.toContract(response.getInitialOrderPrice()))
                .setExecutedOrderPrice(MoneyValueTranslator.toContract(response.getExecutedOrderPrice()))
                .setTotalOrderAmount(MoneyValueTranslator.toContract(response.getTotalOrderAmount()))
                .setInitialCommission(MoneyValueTranslator.toContract(response.getInitialCommission()))
                .setExecutedCommission(MoneyValueTranslator.toContract(response.getExecutedCommission()))
                .setAciValue(MoneyValueTranslator.toContract(response.getAciValue()))
                .setDirection(OrderDirectionTranslator.toContract(response.getDirection()))
                .setInitialSecurityPrice(MoneyValueTranslator.toContract(response.getInitialSecurityPrice()))
                .setOrderType(OrderTypeTranslator.toContract(response.getOrderType()))
                .setMessage(response.getMessage())
                .setInstrumentUid(response.getInstrumentUid())
                .setOrderRequestId(response.getOrderRequestId());
    }
}
