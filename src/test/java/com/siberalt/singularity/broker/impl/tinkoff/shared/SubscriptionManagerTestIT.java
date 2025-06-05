package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.impl.tinkoff.sandbox.TinkoffSandboxBroker;
import com.siberalt.singularity.broker.impl.tinkoff.sandbox.TinkoffSandboxService;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.MoneyValueTranslator;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SubscriptionManagerTestIT {
    protected TinkoffSandboxBroker tinkoffBroker;
    protected String testAccountId;
    protected ConfigInterface configuration;

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

    protected TinkoffSandboxBroker getTinkoffSandbox() throws IOException {
        if (null == tinkoffBroker) {
            configuration = getConfiguration();
            tinkoffBroker = new TinkoffSandboxBroker((String) configuration.get("sandboxToken"));
        }

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
}
