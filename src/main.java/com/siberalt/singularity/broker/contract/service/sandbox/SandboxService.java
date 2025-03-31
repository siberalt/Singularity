package com.siberalt.singularity.broker.contract.service.sandbox;

public interface SandboxService {
    String openAccount(String name);

    void closeAccount(String accountId);
}
