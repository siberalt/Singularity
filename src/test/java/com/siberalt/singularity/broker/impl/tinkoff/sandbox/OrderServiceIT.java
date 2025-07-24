package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ErrorType;
import com.siberalt.singularity.broker.contract.service.exception.InvalidRequestException;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.tinkoff.shared.OrderService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;

public class OrderServiceIT extends AbstractTinkoffSanboxIT {
    @Test
    public void insufficientBalanceException() throws IOException, AbstractException {
        var orderService = getTinkoffSandbox().getOrderService();
        var share = getTestShare();
        var caught = false;

        String accountId = openTestAccount(
            "TestAccount", MoneyValue.newBuilder().setCurrency("RUB").setUnits(1200).build()
        );

        try {
            orderService.post(
                new PostOrderRequest()
                    .setAccountId(accountId)
                    .setDirection(OrderDirection.BUY)
                    .setInstrumentId(share.getUid())
                    .setOrderType(OrderType.BEST_PRICE)
                    .setQuantity(10)
                    .setPrice(Quotation.of(BigDecimal.valueOf(122)))
            );
        } catch (InvalidRequestException exception) {
            ErrorCode errorCode = exception.getErrorCode();
            Assertions.assertEquals(errorCode, ErrorCode.INSUFFICIENT_BALANCE);
            Assertions.assertEquals(errorCode.getCode(), ErrorCode.INSUFFICIENT_BALANCE.getCode());
            Assertions.assertEquals(errorCode.getErrorType(), ErrorType.INVALID_REQUEST);
            caught = true;
        }

        Assertions.assertTrue(caught);
    }
    @Test
    public void calculateOrder() throws IOException, AbstractException {
        var orderService = getTinkoffSandbox().getOrderService();
        var share = getTestShare();
        var testAccountId = openTestAccount("TestAccount");

        System.out.println("Testing share: ");
        System.out.println(share);
        System.out.println("\nTesting method calculate: ");
        var calculateResponse = orderService.calculate(
            new CalculateRequest(
                new PostOrderRequest()
                    .setAccountId(testAccountId)
                    .setInstrumentId(share.getUid())
                    .setDirection(OrderDirection.BUY)
                    .setOrderType(OrderType.MARKET)
                    .setQuantity(120)
            )
        );
        Quotation totalOrderPrice = calculateResponse.instrumentPrice().multiply(120);
        Quotation expectedTotalBalanceChange = totalOrderPrice
            .add(totalOrderPrice.multiply(OrderService.DEFAULT_COMMISSION_RATE))
            .multiply(-1);
        Assertions.assertEquals(share.getUid(), calculateResponse.instrumentUid());
        Assertions.assertEquals(expectedTotalBalanceChange, calculateResponse.totalBalanceChange());
        Assertions.assertEquals(120, calculateResponse.quantity());
        Assertions.assertFalse(calculateResponse.transactionTemplates().isEmpty());
        System.out.println("calculateResponse: " + calculateResponse);
    }

    @Test
    public void cancel() throws IOException, AbstractException {
        var orderService = getTinkoffSandbox().getOrderService();
        var share = getTestShare();
        var testAccountId = openTestAccount("TestAccount");

        var buyOrderResponse = orderService.post(
            new PostOrderRequest()
                .setAccountId(testAccountId)
                .setDirection(OrderDirection.BUY)
                .setInstrumentId(share.getUid())
                .setOrderType(OrderType.LIMIT)
                .setPrice(Quotation.of(BigDecimal.valueOf(12.22)))
                .setQuantity(12)
        );

        var cancelResponse = orderService.cancel(
            new CancelOrderRequest()
                .setOrderId(buyOrderResponse.getOrderId())
                .setAccountId(testAccountId)
        );

        Assertions.assertNotNull(cancelResponse.getTime());
        Assertions.assertFalse(isOrderExists(testAccountId, buyOrderResponse.getOrderId()));
    }

    @Test
    public void postBuyOrder() throws IOException, AbstractException {
        var orderService = getTinkoffSandbox().getOrderService();
        var share = getTestShare();
        var testAccountId = openTestAccount("TestAccount");

        long buyQuantity = 120;
        var buyOrderResponse = orderService.post(
            new PostOrderRequest()
                .setAccountId(testAccountId)
                .setDirection(OrderDirection.BUY)
                .setInstrumentId(share.getUid())
                .setOrderType(OrderType.BEST_PRICE)
                .setQuantity(buyQuantity)
                .setPrice(Quotation.of(BigDecimal.valueOf(122)))
        );

        Assertions.assertEquals(buyQuantity, buyOrderResponse.getLotsExecuted());
        assertInstrumentBalance(testAccountId, share.getUid(), buyQuantity);
    }

    @Test
    public void postSellOrder() throws IOException, AbstractException {
        var orderService = getTinkoffSandbox().getOrderService();
        var share = getTestShare();
        var testAccountId = openTestAccount("TestAccount");

        long sellQuantity = 10;
        var sellOrderResponse = orderService.post(
            new PostOrderRequest()
                .setAccountId(testAccountId)
                .setDirection(OrderDirection.SELL)
                .setInstrumentId(share.getUid())
                .setOrderType(OrderType.BEST_PRICE)
                .setQuantity(sellQuantity)
        );

        Assertions.assertEquals(sellQuantity, sellOrderResponse.getLotsExecuted());
        assertInstrumentBalance(testAccountId, share.getUid(), -sellQuantity);
    }

    @Test
    public void getOrderState() throws IOException, AbstractException {
        var orderService = getTinkoffSandbox().getOrderService();
        var share = getTestShare();
        var testAccountId = openTestAccount("TestAccount");

        var buyOrderResponse = orderService.post(
            new PostOrderRequest()
                .setAccountId(testAccountId)
                .setDirection(OrderDirection.BUY)
                .setInstrumentId(share.getUid())
                .setOrderType(OrderType.LIMIT)
                .setPrice(Quotation.of(BigDecimal.valueOf(12.22)))
                .setQuantity(12)
        );

        var orderState = orderService.getState(
            new GetOrderStateRequest()
                .setOrderId(buyOrderResponse.getOrderId())
                .setPriceType(PriceType.CURRENCY)
                .setAccountId(testAccountId)
        );

        Assertions.assertTrue(
            orderState.getDirection() == OrderDirection.BUY
                && Objects.equals(orderState.getInstrumentUid(), buyOrderResponse.getInstrumentUid())
                && Objects.equals(orderState.getOrderId(), buyOrderResponse.getOrderId())
        );
    }

    @Test
    public void getOrders() throws IOException, AbstractException {
        var orderService = getTinkoffSandbox().getOrderService();
        var testAccountId = openTestAccount("TestAccount");
        var share = getTestShare();

        orderService.post(
            new PostOrderRequest()
                .setAccountId(testAccountId)
                .setDirection(OrderDirection.BUY)
                .setInstrumentId(share.getUid())
                .setOrderType(OrderType.LIMIT)
                .setPrice(Quotation.of(BigDecimal.valueOf(12.22)))
                .setQuantity(12)
        );

        var getResponse = orderService.get(new GetOrdersRequest().setAccountId(testAccountId));

        for (var order : getResponse.getOrders()) {
            System.out.printf("orderId: %s\n", order.getOrderId());
            System.out.printf("orderType: %s\n", order.getOrderType());
            System.out.printf("currency: %s\n", order.getCurrency());
            System.out.printf("orderDate: %s\n", order.getOrderDate());
            System.out.printf("orderRequestId: %s\n", order.getIdempotencyKey());
            System.out.printf("charges: %s\n", order.getTransactions());
            System.out.printf("serviceCommission: %s\n", order.getServiceCommission());
            System.out.printf("executedOrderPrice: %s\n", order.getExecutedOrderPrice());
            System.out.printf("initialOrderPrice: %s\n", order.getInitialOrderPrice());
            System.out.printf("initialSecurityPrice: %s\n", order.getInitialSecurityPrice());
            System.out.printf("averagePositionPrice: %s\n", order.getAveragePositionPrice());
            System.out.printf("lotsRequested: %s\n", order.getLotsRequested());
            System.out.printf("direction: %s\n", order.getDirection());
            System.out.println();
        }
    }
}
