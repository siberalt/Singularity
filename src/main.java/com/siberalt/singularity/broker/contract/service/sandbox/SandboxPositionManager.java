package com.siberalt.singularity.broker.contract.service.sandbox;

public interface SandboxPositionManager {
    void addToPosition(String accountId, String instrumentUid, long amount);
}
