package com.siberalt.singularity.strategy.market.position;

public interface EntryPriceCalculator {
    EntryPrice calculate(String accountId, String instrumentUid);
}
