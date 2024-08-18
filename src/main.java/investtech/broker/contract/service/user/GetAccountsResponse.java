package investtech.broker.contract.service.user;

import java.util.Collection;

public class GetAccountsResponse {
    Collection<Account> accounts;

    public Collection<Account> getAccounts() {
        return accounts;
    }

    public GetAccountsResponse setAccounts(Collection<Account> accounts) {
        this.accounts = accounts;
        return this;
    }
}
