package com.siberalt.singularity.broker.impl.mock.shared.user;

import com.siberalt.singularity.broker.contract.service.user.Account;

import java.time.Instant;

public class AccountState {
    protected boolean blocked;
    protected Instant blockedDate;
    protected Account account;

    public AccountState(Account account) {
        this.account = account;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public AccountState setBlocked(boolean blocked) {
        this.blocked = blocked;
        return this;
    }

    public Instant getBlockedDate() {
        return blockedDate;
    }

    public AccountState setBlockedDate(Instant blockedDate) {
        this.blockedDate = blockedDate;
        return this;
    }

    public boolean isClosed(){
        return null != account.getClosedDate();
    }

    public Account getAccount() {
        return account;
    }

    public AccountState setAccount(Account account) {
        this.account = account;
        return this;
    }
}
