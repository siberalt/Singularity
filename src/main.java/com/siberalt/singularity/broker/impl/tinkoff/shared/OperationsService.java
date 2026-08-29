package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.operation.request.GetOperationsRequest;
import com.siberalt.singularity.broker.contract.service.operation.response.GetOperationsResponse;
import com.siberalt.singularity.broker.contract.service.operation.response.GetPositionsResponse;
import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.MoneyValueTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.OperationTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.PositionSecuritiesTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.TimestampTranslator;
import com.siberalt.singularity.entity.operation.Operation;
import ru.tinkoff.piapi.contract.v1.GetOperationsByCursorRequest;
import ru.tinkoff.piapi.contract.v1.GetOperationsByCursorResponse;
import ru.tinkoff.piapi.contract.v1.OperationsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.PositionsRequest;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class OperationsService implements com.siberalt.singularity.broker.contract.service.operation.OperationsService {
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

    @Override
    public GetOperationsResponse getOperations(GetOperationsRequest request) throws AbstractException {
        List<Operation> operations = new ArrayList<>();
        String cursor = "";
        GetOperationsByCursorResponse response;

        do {
            String currentCursor = cursor;
            response = ExceptionConverter.rethrowContractExceptionOnError(() ->
                    this.operationsBlockingStub.getOperationsByCursor(
                            GetOperationsByCursorRequest
                                    .newBuilder()
                                    .setAccountId(request.getAccountId())
                                    .setFrom(TimestampTranslator.toTinkoff(request.getFrom()))
                                    .setTo(TimestampTranslator.toTinkoff(request.getTo()))
                                    .setCursor(currentCursor)
                                    .build()
                    )
            );

            operations.addAll(
                    ListTranslator.translate(
                            response.getItemsList(),
                            item -> OperationTranslator.toContract(item, request.getAccountId())
                    )
            );

            cursor = response.getNextCursor();
        } while (response.getHasNext());

        return new GetOperationsResponse().setOperations(operations);
    }
}
