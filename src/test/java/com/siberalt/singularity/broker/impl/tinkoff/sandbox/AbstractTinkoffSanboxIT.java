package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetOrdersRequest;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.MoneyValueTranslator;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.instrument.Instrument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;

public abstract class AbstractTinkoffSanboxIT {
    protected TinkoffSandboxBroker tinkoffBroker;
    protected ConfigInterface configuration;
    protected String testAccountId;
    protected Instrument testShare;

    @BeforeEach
    protected void setUp() throws IOException, AbstractException {
        configuration = getConfiguration();
        tinkoffBroker = new TinkoffSandboxBroker((String) configuration.get("sandboxToken"));
    }

    @AfterEach
    protected void clearUp() {
        tinkoffBroker.close();
    }

    protected Instrument getTestShare() throws IOException, AbstractException {
        if (null == testShare) {
            var response = getTinkoffSandbox().getInstrumentService()
                .get(GetRequest.of((String) getConfiguration().get("shareIsin")));
            testShare = response.getInstrument();
        }

        return testShare;
    }

    protected String openTestAccount(String name) throws IOException, AbstractException {
        return openTestAccount(name, MoneyValue.newBuilder().setCurrency("RUB").setUnits(120000).build());
    }

    protected String openTestAccount(String name, MoneyValue startBalance) throws IOException, AbstractException {
        var tinkoffBroker = getTinkoffSandbox();
        var responseAccounts = tinkoffBroker.getUserService().getAccounts(null);
        TinkoffSandboxService sandboxService = tinkoffBroker.getSandboxService();

        if (!responseAccounts.getAccounts().isEmpty()) {
            responseAccounts
                .getAccounts()
                .stream()
                .map(Account::getId)
                .forEach(sandboxService::closeAccount);
        }

        testAccountId = sandboxService.openAccount(name);
        sandboxService.payIn(testAccountId, MoneyValueTranslator.toContract(startBalance));

        return testAccountId;
    }

    protected TinkoffSandboxBroker getTinkoffSandbox() {
        return tinkoffBroker;
    }

    protected ConfigInterface getConfiguration() throws IOException {
        if (null == configuration) {
            configuration = new YamlConfig(
                Files.newInputStream(Paths.get("src/test/resources/broker/tinkoff/test-settings.yaml"))
            );
        }

        return configuration;
    }

    protected boolean isOrderExists(String accountId, String orderId) throws IOException, AbstractException {
        return getTinkoffSandbox()
            .getOrderService()
            .get(GetOrdersRequest.of(accountId))
            .getOrders()
            .stream()
            .anyMatch(x -> Objects.equals(x.getOrderId(), orderId));
    }

    protected void assertInstrumentBalance(String accountId, String instrumentUid, long expectedBalance) throws IOException, AbstractException {
        var instrumentPosition = getTinkoffSandbox()
            .getOperationsService()
            .getPositions(GetPositionsRequest.of(accountId))
            .getSecurities()
            .stream()
            .filter(x -> Objects.equals(x.getInstrumentUid(), instrumentUid))
            .findFirst()
            .orElse(null);

        if (null == instrumentPosition && expectedBalance > 0) {
            Assertions.fail("Instrument position should be present. Expected balance: " + expectedBalance);
        }

        Assertions.assertEquals(instrumentPosition == null ? 0 : instrumentPosition.getBalance(), expectedBalance);
    }
}
