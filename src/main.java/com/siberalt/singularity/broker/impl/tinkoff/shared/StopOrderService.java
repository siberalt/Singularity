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
import ru.tinkoff.piapi.core.StopOrdersService;

public class StopOrderService implements StopOrderServiceInterface {
    protected StopOrdersService stopOrdersServiceApi;

    public StopOrderService(StopOrdersService stopOrdersServiceApi) {
        this.stopOrdersServiceApi = stopOrdersServiceApi;
    }

    @Override
    public PostStopOrderResponse post(PostStopOrderRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
                () -> stopOrdersServiceApi.postStopSync(
                        request.getInstrumentId(),
                        request.getQuantity(),
                        QuotationTranslator.toTinkoff(request.getPrice()),
                        QuotationTranslator.toTinkoff(request.getStopPrice()),
                        StopOrderDirectionTranslator.toTinkoff(request.getDirection()),
                        request.getAccountId(),
                        StopOrderTypeTranslator.toTinkoff(request.getStopOrderType()),
                        StopOrderExpirationTypeTranslator.toTinkoff(request.getExpirationType()),
                        TakeProfitTypeTranslator.toTinkoff(request.getTakeProfitType()),
                        PostStopOrderTrailingDataTranslator.toTinkoff(request.getTrailingData()),
                        request.getExpireDate()
                )
        );

        return new PostStopOrderResponse()
                .setStopOrderId(response);
    }

    @Override
    public GetStopOrdersResponse get(GetStopOrdersRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
                () -> stopOrdersServiceApi.getStopOrdersSync(
                        request.getAccountId(),
                        request.getFrom(),
                        request.getTo(),
                        StopOrderStatusOptionTranslator.toTinkoff(request.getStatus())
                )
        );

        return new GetStopOrdersResponse()
                .setStopOrders(ListTranslator.translate(response, StopOrderTranslator::toContract));
    }

    @Override
    public CancelStopOrderResponse cancel(CancelStopOrderRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
                () -> stopOrdersServiceApi.cancelStopOrderSync(request.getAccountId(), request.getStopOrderId())
        );

        return new CancelStopOrderResponse()
                .setTime(response);
    }
}
