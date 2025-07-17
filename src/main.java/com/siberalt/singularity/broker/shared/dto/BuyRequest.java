package com.siberalt.singularity.broker.shared.dto;

import com.siberalt.singularity.broker.contract.service.order.request.OrderType;

public record BuyRequest(
    String accountId,
    String instrumentId,
    OrderType orderType
) {
}