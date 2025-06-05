package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.user.*;
import com.siberalt.singularity.broker.impl.mock.shared.user.AccountState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MockUserService implements UserService {
    protected MockBroker virtualBroker;
    protected Map<String, AccountState> accountsStates = new HashMap<>();

    public MockUserService(MockBroker virtualBroker) {
        this.virtualBroker = virtualBroker;
    }

    protected AccountState getAccountState(String accountId) throws AbstractException {
        checkAccountExists(accountId);

        return accountsStates.get(accountId);
    }

    public boolean accountExists(String accountId) {
        return accountsStates.containsKey(accountId);
    }

    public Account openAccount(String name, AccountType accountType, AccessLevel accessLevel) {
        Account account = new Account()
                .setName(name)
                .setType(accountType)
                .setAccessLevel(accessLevel);
        account.setOpenedDate(virtualBroker.context.getCurrentTime());
        account.setId(UUID.randomUUID().toString());
        account.setStatus(AccountStatus.OPEN);
        accountsStates.put(account.getId(), new AccountState(account));

        return account;
    }

    public void closeAccount(String accountId) throws AbstractException {
        checkAccountExists(accountId);

        var account = accountsStates.get(accountId).getAccount();

        assert null == account.getClosedDate() : ExceptionBuilder.create(ErrorCode.ACCOUNT_CLOSED);

        account.setClosedDate(virtualBroker.context.getCurrentTime());
        account.setStatus(AccountStatus.CLOSED);
    }

    @Override
    public GetAccountsResponse getAccounts(GetAccountsRequest request) {
        return new GetAccountsResponse()
                .setAccounts(accountsStates.values().stream().map(AccountState::getAccount).toList());
    }

    void checkAccountExists(String accountId) throws AbstractException {
        if (!accountExists(accountId)) {
            throw ExceptionBuilder.create(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }
}
