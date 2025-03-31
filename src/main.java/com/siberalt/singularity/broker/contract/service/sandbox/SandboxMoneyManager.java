package com.siberalt.singularity.broker.contract.service.sandbox;

import com.siberalt.singularity.broker.contract.value.money.Money;

public interface SandboxMoneyManager {
    void payIn(String accountId, Money money);
}
