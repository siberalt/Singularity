package com.siberalt.singularity.broker.shared;

import com.siberalt.singularity.broker.contract.execution.Broker;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetCurrentPriceRequest;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.contract.service.order.OrderService;
import com.siberalt.singularity.broker.contract.service.order.request.CalculateRequest;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.PostOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.response.CalculateResponse;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.shared.dto.BuyRequest;

public class OrderCalculationService {
    public long calculatePossibleBuyQuantity(Broker broker, Quotation limit, BuyRequest request) throws AbstractException {
        // Retrieve the current price of the instrument
        Quotation instrumentPrice = broker.getMarketDataService()
            .getCurrentPrice(new GetCurrentPriceRequest(request.instrumentId()))
            .getPrice();

        // Call the refactored method
        return calculatePossibleBuyQuantity(broker.getOrderService(), limit, instrumentPrice, request);
    }

    public long calculatePossibleBuyQuantity(Broker broker, BuyRequest request) throws AbstractException {
        // Retrieve the currency of the instrument
        String instrumentCurrency = broker.getInstrumentService()
            .get(GetRequest.of(request.instrumentId()))
            .getInstrument()
            .getCurrency();

        // Retrieve the balance for the account in the instrument's currency
        Quotation balance = broker.getOperationsService()
            .getPositions(GetPositionsRequest.of(request.accountId()))
            .getMoney()
            .stream()
            .filter(money -> money.getCurrencyIso().equals(instrumentCurrency))
            .findFirst()
            .orElse(Money.of(instrumentCurrency, Quotation.ZERO))
            .getQuotation();

        // Retrieve the current price of the instrument
        Quotation instrumentPrice = broker.getMarketDataService()
            .getCurrentPrice(new GetCurrentPriceRequest(request.instrumentId()))
            .getPrice();

        // Call the refactored method
        return calculatePossibleBuyQuantity(
            broker.getOrderService(),
            balance,
            instrumentPrice,
            new BuyRequest(request.accountId(), request.instrumentId(), request.orderType())
        );
    }

    public long calculatePossibleBuyQuantity(
        OrderService orderService,
        Quotation limit,
        Quotation instrumentPrice,
        BuyRequest request
    )
        throws AbstractException {
        long amount;
        CalculateResponse response;
        String accountId = request.accountId();
        String instrumentId = request.instrumentId();

        if (instrumentPrice.isLessOrEqual(Quotation.ZERO)) {
            // If the price is negative, throw an exception
            throw new ArithmeticException("Price per unit cannot be negative or zero.");
        }

        if (limit.isZero() || limit.isLess(instrumentPrice)) {
            // If the price or limit is zero, return zero quantity
            return 0;
        }

        // Loop to calculate the maximum quantity that can be bought
        do {
            // Calculate the maximum quantity that can be bought
            amount = limit.divide(instrumentPrice).toBigDecimal().longValue();

            // Get the response for the calculated order
            response = orderService.calculate(CalculateRequest.of(
                new PostOrderRequest()
                    .setAccountId(accountId)
                    .setInstrumentId(instrumentId)
                    .setQuantity(amount)
                    .setDirection(OrderDirection.BUY)
                    .setOrderType(request.orderType()))
            );

            if (amount <= 0) {
                // If the calculated amount is zero or negative, break the loop
                break;
            }

            // Update the price per unit based on the response
            instrumentPrice = response.totalBalanceChange().divide(amount);
        } while (response.totalBalanceChange().isGreaterThan(limit));

        return amount;
    }
}
