package com.siberalt.singularity.broker.contract.service.user;

import java.util.List;

public class GetAccountsResponse {
    List<Account> accounts;

    public List<Account> getAccounts() {
        return accounts;
    }

    public GetAccountsResponse setAccounts(List<Account> accounts) {
        this.accounts = accounts;
        return this;
    }
}
