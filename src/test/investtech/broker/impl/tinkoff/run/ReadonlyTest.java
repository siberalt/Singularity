package investtech.broker.impl.tinkoff.run;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.operation.request.GetPositionsRequest;
import investtech.broker.contract.service.user.Account;
import investtech.broker.impl.tinkoff.emulation.TinkoffSandboxBroker;
import investtech.configuration.ConfigurationInterface;
import investtech.configuration.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class ReadonlyTest {
    @Test
    public void test() throws IOException, AbstractException {
        ConfigurationInterface configuration = new YamlConfiguration(
                Files.newInputStream(Paths.get("src/test/resources/broker/tinkoff/test-settings.yaml"))
        );

        var tinkoffBroker = new TinkoffReadonlyBroker((String) configuration.get("readonlyToken"));

        var response = tinkoffBroker.getUserService().getAccounts(null);
        var accountIds = response.getAccounts().stream().map(Account::getId).collect(Collectors.toList());
        var operationService = tinkoffBroker.getOperationsService();

        for (String accountId : accountIds) {
            var positionsResponse = operationService.getPositions(GetPositionsRequest.of(accountId));

            System.out.printf("Account [%s]\n", accountId);
            System.out.println("Securities:\n");

            for (var security : positionsResponse.getSecurities()) {
                System.out.printf("InstrumentUid: %s\n", security.getInstrumentUid());
                System.out.printf("InstrumentType: %s\n", security.getInstrumentType());
                System.out.printf("Balance: %s\n", security.getBalance());
                System.out.printf("Blocked: %s\n", security.getBlocked());
                System.out.printf("PositionUid: %s\n", security.getPositionUid());
                System.out.println();
            }
        }
    }
}
