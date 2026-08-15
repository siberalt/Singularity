package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.order.stop.request.CancelStopOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.stop.request.GetStopOrdersRequest;
import com.siberalt.singularity.broker.contract.service.order.stop.request.PostStopOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.stop.response.CancelStopOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.stop.response.GetStopOrdersResponse;
import com.siberalt.singularity.broker.contract.service.order.stop.response.PostStopOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.stop.StopOrderServiceInterface;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.*;
import ru.tinkoff.piapi.contract.v1.StopOrdersServiceGrpc;

public class StopOrderService implements StopOrderServiceInterface {
    protected StopOrdersServiceGrpc.StopOrdersServiceBlockingStub stopOrdersServiceApi;

    public StopOrderService(StopOrdersServiceGrpc.StopOrdersServiceBlockingStub stopOrdersServiceApi) {
        this.stopOrdersServiceApi = stopOrdersServiceApi;
    }

    @Override
    public PostStopOrderResponse post(PostStopOrderRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
            () -> stopOrdersServiceApi.postStopOrder(
                ru.tinkoff.piapi.contract.v1.PostStopOrderRequest.newBuilder()
                    .setInstrumentId(request.getInstrumentId())
                    .setQuantity(request.getQuantity())
                    .setPrice(QuotationTranslator.toTinkoff(request.getPrice()))
                    .setStopPrice(QuotationTranslator.toTinkoff(request.getStopPrice()))
                    .setDirection(StopOrderDirectionTranslator.toTinkoff(request.getDirection()))
                    .setAccountId(request.getAccountId())
                    .setStopOrderType(StopOrderTypeTranslator.toTinkoff(request.getStopOrderType()))
                    .setExpirationType(StopOrderExpirationTypeTranslator.toTinkoff(request.getExpirationType()))
                    .setTakeProfitType(TakeProfitTypeTranslator.toTinkoff(request.getTakeProfitType()))
                    .setTrailingData(PostStopOrderTrailingDataTranslator.toTinkoff(request.getTrailingData()))
                    .setExpireDate(TimestampTranslator.toTinkoff(request.getExpireDate()))
                    .build()
            )
        );

        return new PostStopOrderResponse()
            .setStopOrderId(response.getStopOrderId());
    }

    @Override
    public GetStopOrdersResponse get(GetStopOrdersRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
            () -> stopOrdersServiceApi.getStopOrders(
                ru.tinkoff.piapi.contract.v1.GetStopOrdersRequest.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setFrom(TimestampTranslator.toTinkoff(request.getFrom()))
                    .setTo(TimestampTranslator.toTinkoff(request.getTo()))
                    .setStatus(StopOrderStatusOptionTranslator.toTinkoff(request.getStatus()))
                    .build()
            )
        );

        return new GetStopOrdersResponse()
            .setStopOrders(ListTranslator.translate(response.getStopOrdersList(), StopOrderTranslator::toContract));
    }

    @Override
    public CancelStopOrderResponse cancel(CancelStopOrderRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
            () -> stopOrdersServiceApi.cancelStopOrder(
                ru.tinkoff.piapi.contract.v1.CancelStopOrderRequest.newBuilder()
                    .setAccountId(request.getAccountId())
                    .setStopOrderId(request.getStopOrderId())
                    .build()
            )
        );

        return new CancelStopOrderResponse()
            .setTime(TimestampTranslator.toContract(response.getTime()));
    }
}
