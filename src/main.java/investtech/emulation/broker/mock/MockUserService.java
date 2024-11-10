package investtech.emulation.broker.mock;

import investtech.broker.contract.service.exception.ErrorCode;
import investtech.broker.contract.service.exception.ExceptionBuilder;
import investtech.broker.contract.service.user.Account;
import investtech.broker.contract.service.user.GetAccountsRequest;
import investtech.broker.contract.service.user.GetAccountsResponse;
import investtech.broker.contract.service.user.UserServiceInterface;
import investtech.emulation.broker.mock.user.AccountState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MockUserService implements UserServiceInterface {
    protected MockBroker virtualBroker;
    protected Map<String, AccountState> accountsStates = new HashMap<>();

    public MockUserService(MockBroker virtualBroker) {
        this.virtualBroker = virtualBroker;
    }

    protected AccountState getAccountState(String accountId) {
        assert accountsStates.containsKey(accountId) : ExceptionBuilder.create(ErrorCode.ACCOUNT_NOT_FOUND);

        return accountsStates.get(accountId);
    }

    protected Account openAccount(Account account) {
        account.setOpenedDate(virtualBroker.context.getCurrentTime());
        account.setId(UUID.randomUUID().toString());
        accountsStates.put(account.getId(), new AccountState(account));

        return account;
    }

    protected void closeAccount(String accountId) {
        assert accountsStates.containsKey(accountId) : ExceptionBuilder.create(ErrorCode.ACCOUNT_NOT_FOUND);

        var account = accountsStates.get(accountId).getAccount();

        assert null == account.getClosedDate() : ExceptionBuilder.create(ErrorCode.ACCOUNT_CLOSED);

        account.setClosedDate(virtualBroker.context.getCurrentTime());
    }

    @Override
    public GetAccountsResponse getAccounts(GetAccountsRequest request) {
        return new GetAccountsResponse()
                .setAccounts(accountsStates.values().stream().map(AccountState::getAccount).toList());
    }
}
