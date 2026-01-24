package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.PermissionDeniedException;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class TinkoffSandboxBrokerIT extends AbstractTinkoffSanboxIT {
    @Test
    public void testOperationsService() throws IOException, AbstractException {
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
    public void permissionDeniedExceptionHandling() throws IOException {
        try (TinkoffSandboxBroker finalTinkoffBroker = new TinkoffSandboxBroker(
            getConfiguration().get("sandboxToken") + "123"
        )) {
            Assertions.assertThrows(
                PermissionDeniedException.class, () -> finalTinkoffBroker.getUserService().getAccounts(null)
            );
        }
    }
}
