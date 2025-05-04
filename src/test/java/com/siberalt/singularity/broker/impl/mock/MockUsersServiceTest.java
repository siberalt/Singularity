package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.user.AccessLevel;
import com.siberalt.singularity.broker.contract.service.user.AccountStatus;
import com.siberalt.singularity.broker.contract.service.user.AccountType;
import com.siberalt.singularity.broker.contract.service.user.GetAccountsRequest;
import com.siberalt.singularity.strategy.context.simulation.time.ClockStub;
import com.siberalt.singularity.strategy.simulation.SimulationContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

public class MockUsersServiceTest {
    @Test
    void testBasic() throws AbstractException {
        var openDate = Instant.parse("2020-12-30T07:00:00Z");
        var clock = new ClockStub();
        clock.syncCurrentTime(openDate);
        var mockBroker = new MockBroker(null, null, null);
        mockBroker.applyContext(new SimulationContext(null, null, clock));

        var userService = mockBroker.getUserService();
        var accountName = "testAccount";
        var accountType = AccountType.ORDINARY;
        var accessLevel = AccessLevel.FULL_ACCESS;

        // Test openAccount
        var account = userService.openAccount(accountName, accountType, accessLevel);

        Assertions.assertNotNull(account.getId());
        Assertions.assertEquals(accountName, account.getName());
        Assertions.assertEquals(AccountStatus.OPEN, account.getStatus());
        Assertions.assertNull(account.getClosedDate());
        Assertions.assertEquals(accessLevel, account.getAccessLevel());
        Assertions.assertEquals(accountType, account.getType());
        Assertions.assertEquals(openDate, account.getOpenedDate());

        var accounts = userService.getAccounts(new GetAccountsRequest());
        Assertions.assertEquals(1, accounts.getAccounts().size());

        account = accounts.getAccounts().stream().findFirst().orElseThrow();

        Assertions.assertNotNull(account.getId());
        Assertions.assertEquals(accountName, account.getName());
        Assertions.assertEquals(AccountStatus.OPEN, account.getStatus());
        Assertions.assertNull(account.getClosedDate());
        Assertions.assertEquals(accessLevel, account.getAccessLevel());
        Assertions.assertEquals(accountType, account.getType());
        Assertions.assertEquals(openDate, account.getOpenedDate());

        // Test closeAccount
        var closeDate = Instant.parse("2020-12-31T07:00:00Z");
        clock.syncCurrentTime(closeDate);
        userService.closeAccount(account.getId());

        accounts = userService.getAccounts(new GetAccountsRequest());
        Assertions.assertEquals(1, accounts.getAccounts().size());

        account = accounts.getAccounts().stream().findFirst().orElseThrow();
        Assertions.assertNotNull(account.getId());
        Assertions.assertEquals(accountName, account.getName());
        Assertions.assertEquals(AccountStatus.CLOSED, account.getStatus());
        Assertions.assertEquals(closeDate, account.getClosedDate());
        Assertions.assertEquals(accessLevel, account.getAccessLevel());
        Assertions.assertEquals(accountType, account.getType());
        Assertions.assertEquals(openDate, account.getOpenedDate());
    }
}
