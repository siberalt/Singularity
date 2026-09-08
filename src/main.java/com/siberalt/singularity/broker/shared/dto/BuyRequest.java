package com.siberalt.singularity.broker.shared.dto;

import com.siberalt.singularity.broker.contract.service.order.request.OrderType;

/**
 * @param orderType the kind of order the answer is for. A quote is only worth having if it prices
 *                  the order that will actually be sent: ask what a market order costs and then
 *                  send a best-price one, and the size that came back was computed against a price
 *                  the account will not get. It survived only on the margin
 *                  {@code OrderCalculationService} leaves for the price moving, and stopped
 *                  surviving as soon as the two types were priced further apart than that margin.
 */
public record BuyRequest(
    String accountId,
    String instrumentId,
    OrderType orderType
) {
    public BuyRequest(String accountId, String instrumentId) {
        this(accountId, instrumentId, OrderType.MARKET);
    }
}
