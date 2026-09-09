package com.siberalt.singularity.broker.shared;

import com.siberalt.singularity.broker.contract.execution.Broker;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetCurrentPriceRequest;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.contract.service.order.GetMaxLotsOrderService;
import com.siberalt.singularity.broker.contract.service.order.OrderService;
import com.siberalt.singularity.broker.contract.service.order.request.GetMaxLotsRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetPriceRequest;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.PostOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.response.GetMaxLotsResponse;
import com.siberalt.singularity.broker.contract.service.order.response.GetPriceResponse;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.shared.dto.BuyRequest;

public class OrderCalculationService {
    /**
     * Three per cent held back from every buy.
     * <p>
     * What it guards against is the price moving between the quote and the order: the size is worked
     * out against what the instrument costs now, and by the time the order reaches the exchange it
     * may cost more, which would have the account short of what it just committed to.
     * <p>
     * What it costs is the same three per cent of every position, every time - not lost, but never
     * invested. That looks like a price worth removing in a simulation, and it is not: a simulation
     * with any execution latency prices the fill from a later bar than the quote, which is the very
     * gap this covers. Set to zero, a walk-forward over one share died of INSUFFICIENT_BALANCE -
     * every buy sized to the last rouble, and the bar that filled it wanted a rouble more.
     * <p>
     * So it is a real margin against a real risk, and the number is a guess about how far a price
     * can move in the time an order takes to arrive. Three per cent is generous for a bar or two
     * and mean for a day; it deserves to be set from the same knowledge as the execution latency it
     * is covering, rather than left at a default.
     * <p>
     * It also spent a long time hiding a real fault. The quote used to be taken for a market order
     * while the order sent was a best-price one, and the two agreed only because this margin was
     * wider than they disagreed; the day they disagreed by more, the run died of insufficient funds.
     * A margin is a margin, not a way to keep mismatched prices from meeting.
     */
    public static final double DEFAULT_PRICE_SAFETY_MARGIN = 0.03;

    private double priceSafetyMargin = DEFAULT_PRICE_SAFETY_MARGIN;

    public OrderCalculationService(double priceSafetyMargin) {
        setPriceSafetyMargin(priceSafetyMargin);
    }

    public OrderCalculationService() {
    }

    public double getPriceSafetyMargin() {
        return priceSafetyMargin;
    }

    /**
     *  #DEFAULT_PRICE_SAFETY_MARGIN
     */
    public OrderCalculationService setPriceSafetyMargin(double priceSafetyMargin) {
        if (priceSafetyMargin < 0 || priceSafetyMargin >= 1) {
            throw new IllegalArgumentException(
                String.format("Price safety margin must be within [0, 1), got %s", priceSafetyMargin)
            );
        }

        this.priceSafetyMargin = priceSafetyMargin;
        return this;
    }

    public long calculateMaxBuyQuantity(Broker broker, Quotation limit, BuyRequest request) throws AbstractException {
        // Retrieve the current price of the instrument
        Quotation instrumentPrice = broker.getMarketDataService()
            .getCurrentPrice(new GetCurrentPriceRequest(request.instrumentId()))
            .getPrice();

        if (broker.getOrderService() instanceof GetMaxLotsOrderService) {
            GetMaxLotsResponse response = ((GetMaxLotsOrderService) broker.getOrderService())
                .getMaxLots(new GetMaxLotsRequest(request.accountId(), request.instrumentId(), Quotation.ZERO));
            return response.buyLimits().buyMaxLots();
        }

        // Call the refactored method
        return calculateMaxBuyQuantity(broker.getOrderService(), limit, instrumentPrice, request);
    }

    public long calculateMaxBuyQuantity(Broker broker, BuyRequest request) throws AbstractException {
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
        return calculateMaxBuyQuantity(
            broker.getOrderService(),
            balance,
            instrumentPrice,
            request
        );
    }

    public long calculateMaxBuyQuantity(
        OrderService orderService,
        Quotation limit,
        Quotation instrumentPrice,
        BuyRequest request
    )
        throws AbstractException {
        long amount;
        GetPriceResponse response;
        String accountId = request.accountId();
        String instrumentId = request.instrumentId();

        if (instrumentPrice.isLessOrEqual(Quotation.ZERO)) {
            // If the price is negative, throw an exception
            throw new ArithmeticException("Price per unit cannot be negative or zero.");
        }

        limit = limit.subtract(limit.multiply(Quotation.of(priceSafetyMargin)));

        if (limit.isZero() || limit.isLessThan(instrumentPrice)) {
            // If the price or limit is zero, return zero quantity
            return 0;
        }

        // Loop to calculate the maximum quantity that can be bought
        do {
            // Calculate the maximum quantity that can be bought
            amount = limit.divide(instrumentPrice).toBigDecimal().longValue();

            if (amount <= 0) {
                // If the calculated amount is zero or negative, break the loop
                break;
            }

            // Get the response for the calculated order
            response = orderService.getPrice(GetPriceRequest.of(
                new PostOrderRequest()
                    .setAccountId(accountId)
                    .setInstrumentId(instrumentId)
                    .setQuantity(amount)
                    .setOrderType(request.orderType())
                    .setDirection(OrderDirection.BUY)
                )
            );

            // Update the price per unit based on the response
            Quotation previousPrice = instrumentPrice;
            instrumentPrice = response.totalBalanceChange().divide(amount);

            if (instrumentPrice.equals(previousPrice)) {
                // Prevent infinite loop if the price does not change
                break;
            }
        } while (response.totalBalanceChange().isGreaterThan(limit));

        return amount;
    }
}
