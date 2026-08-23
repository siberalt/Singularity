package com.siberalt.singularity.broker.shared.dto;

public record BuyRequest(
    String accountId,
    String instrumentId
) {
}