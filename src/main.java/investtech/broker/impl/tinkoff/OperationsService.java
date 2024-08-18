package investtech.broker.impl.tinkoff;

import investtech.broker.common.ListTranslator;
import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.operation.OperationsServiceInterface;
import investtech.broker.contract.service.operation.request.GetPositionsRequest;
import investtech.broker.contract.service.operation.response.GetPositionsResponse;
import investtech.broker.impl.tinkoff.exception.ExceptionConverter;
import investtech.broker.impl.tinkoff.translation.MoneyValueTranslator;
import investtech.broker.impl.tinkoff.translation.PositionSecuritiesTranslator;
import ru.tinkoff.piapi.contract.v1.OperationsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.PositionsRequest;

import javax.annotation.Nonnull;

public class OperationsService implements OperationsServiceInterface {
    protected final OperationsServiceGrpc.OperationsServiceBlockingStub operationsBlockingStub;

    public OperationsService(
            @Nonnull OperationsServiceGrpc.OperationsServiceBlockingStub operationsBlockingStub
    ) {
        this.operationsBlockingStub = operationsBlockingStub;
    }

    @Override
    public GetPositionsResponse getPositions(GetPositionsRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() ->
                this.operationsBlockingStub.getPositions(
                        PositionsRequest
                                .newBuilder()
                                .setAccountId(request.getAccountId())
                                .build()
                )
        );

        // TODO: fix that
        return new GetPositionsResponse()
                .setBlocked(ListTranslator.translate(response.getBlockedList(), MoneyValueTranslator::toContract))
                .setMoney(ListTranslator.translate(response.getMoneyList(), MoneyValueTranslator::toContract))
                .setSecurities(
                        ListTranslator.translate(response.getSecuritiesList(), PositionSecuritiesTranslator::toContract)
                );
    }
}
