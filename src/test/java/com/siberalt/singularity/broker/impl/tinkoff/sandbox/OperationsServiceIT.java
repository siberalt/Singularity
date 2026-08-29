package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.operation.request.GetOperationsRequest;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class OperationsServiceIT extends AbstractTinkoffSanboxIT {
    @Test
    public void getPositions() throws IOException, AbstractException {
        var tinkoffBroker = getTinkoffSandbox();
        var testAccountId = openTestAccount("TestAccount");

        System.out.println();
        System.out.println("Positions: ");
        var responsePositions = tinkoffBroker.getOperationsService().getPositions(GetPositionsRequest.of(testAccountId));

        for (var position : responsePositions.getSecurities()) {
            System.out.printf("blocked: %s\n", position.getBlocked());
            System.out.printf("positionUid: %s\n", position.getPositionUid());
            System.out.printf("instrumentType: %s\n", position.getInstrumentType());
            System.out.printf("balance: %s\n", position.getBalance());
            System.out.printf("instrumentUid: %s\n", position.getInstrumentUid());
        }
    }

    @Test
    public void getOperations() throws IOException, AbstractException {
        var tinkoffBroker = getTinkoffSandbox();
        var testAccountId = openTestAccount("TestAccount");

        System.out.println();
        System.out.println("Operations: ");
        var responseOperations = tinkoffBroker.getOperationsService().getOperations(
            new GetOperationsRequest()
                .setAccountId(testAccountId)
                .setFrom(Instant.now().minus(30, ChronoUnit.DAYS))
                .setTo(Instant.now())
        );

        for (var operation : responseOperations.getOperations()) {
            System.out.printf("id: %s\n", operation.id());
            System.out.printf("instrumentUid: %s\n", operation.instrumentUid());
            System.out.printf("direction: %s\n", operation.direction());
            System.out.printf("state: %s\n", operation.state());
            System.out.printf("quantity: %s / done: %s\n", operation.quantity(), operation.quantityDone());
            System.out.printf("price: %s\n", operation.price());
            System.out.printf("payment: %s\n", operation.payment());
            System.out.printf("date: %s\n", operation.date());
        }
    }
}
