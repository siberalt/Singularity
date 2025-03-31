package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.exception.*;
import com.siberalt.singularity.broker.contract.service.instrument.Instrument;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.market.request.*;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.contract.service.order.request.PriceType;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.MoneyValueTranslator;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Objects;

public class TinkoffSandboxBrokerTest {
    protected TinkoffSandboxBroker tinkoffBroker;
    protected ConfigInterface configuration;
    protected String testAccountId;
    protected Instrument testShare;

    @Test
    public void testOrderService() throws IOException, AbstractException {
        var tinkoffBroker = getTinkoffSandbox();
        var orderService = tinkoffBroker.getOrderService();
        var share = getTestShare();
        var testAccountId = openTestAccount("TestAccount");

        System.out.println("Testing share: ");
        System.out.println(share);

        System.out.println("\nTesting method post (buy order): ");
        long buyQuantity = 120, instrumentBalance = 0;

        var buyOrderResponse = orderService.post(
                new PostOrderRequest()
                        .setAccountId(testAccountId)
                        .setDirection(OrderDirection.BUY)
                        .setInstrumentId(share.getUid())
                        .setOrderType(OrderType.BEST_PRICE)
                        .setQuantity(buyQuantity)
                        .setPrice(Quotation.of(BigDecimal.valueOf(122)))
        );
        instrumentBalance += buyQuantity;
        Assertions.assertEquals(buyQuantity, buyOrderResponse.getLotsExecuted());
        assertInstrumentBalance(testAccountId, share.getUid(), instrumentBalance);

        buyOrderResponse = orderService.post(
                new PostOrderRequest()
                        .setAccountId(testAccountId)
                        .setDirection(OrderDirection.BUY)
                        .setInstrumentId(share.getUid())
                        .setOrderType(OrderType.BEST_PRICE)
                        .setQuantity(buyQuantity)
                        .setPrice(Quotation.of(BigDecimal.valueOf(122)))
        );
        instrumentBalance += buyQuantity;
        Assertions.assertEquals(buyQuantity, buyOrderResponse.getLotsExecuted());
        assertInstrumentBalance(testAccountId, share.getUid(), instrumentBalance);

        System.out.println("\nTesting method post (sell order): ");
        long sellQuantity = 10;
        var sellOrderResponse = orderService.post(
                new PostOrderRequest()
                        .setAccountId(testAccountId)
                        .setDirection(OrderDirection.SELL)
                        .setInstrumentId(share.getUid())
                        .setOrderType(OrderType.BEST_PRICE)
                        .setQuantity(sellQuantity)
        );
        instrumentBalance -= sellQuantity;
        Assertions.assertEquals(sellQuantity, sellOrderResponse.getLotsExecuted());
        assertInstrumentBalance(testAccountId, share.getUid(), instrumentBalance);

        System.out.println("\nTesting method getState: ");
        buyOrderResponse = orderService.post(
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

        System.out.println("\nTesting method get: ");
        var getResponse = orderService.get(new GetOrdersRequest().setAccountId(testAccountId));

        for (var order : getResponse.getOrders()) {
            System.out.printf("orderId: %s\n", order.getOrderId());
            System.out.printf("orderType: %s\n", order.getOrderType());
            System.out.printf("currency: %s\n", order.getCurrency());
            System.out.printf("orderDate: %s\n", order.getOrderDate());
            System.out.printf("orderRequestId: %s\n", order.getIdempotencyKey());
            System.out.printf("executedCommission: %s\n", order.getExecutedCommission());
            System.out.printf("serviceCommission: %s\n", order.getServiceCommission());
            System.out.printf("initialCommission: %s\n", order.getInitialCommission());
            System.out.printf("executedOrderPrice: %s\n", order.getExecutedOrderPrice());
            System.out.printf("initialOrderPrice: %s\n", order.getInitialOrderPrice());
            System.out.printf("initialSecurityPrice: %s\n", order.getInitialSecurityPrice());
            System.out.printf("averagePositionPrice: %s\n", order.getAveragePositionPrice());
            System.out.printf("lotsRequested: %s\n", order.getLotsRequested());
            System.out.printf("direction: %s\n", order.getDirection());
            System.out.println();
        }

        System.out.println("\nTesting method cancel: ");
        orderService.cancel(
                new CancelOrderRequest()
                        .setOrderId(buyOrderResponse.getOrderId())
                        .setAccountId(testAccountId)
        );

        Assertions.assertFalse(isOrderExists(testAccountId, buyOrderResponse.getOrderId()));
    }

    @Test
    public void testOperationsService() throws IOException, AbstractException {
        var tinkoffBroker = getTinkoffSandbox();
        var testAccountId = openTestAccount("TestAccount");

        System.out.println();
        System.out.println("Positions: ");
        var responsePositions = tinkoffBroker.getOperationsService().getPositions(GetPositionsRequest.of(testAccountId));

        for (var position : responsePositions.getSecurities()) {
            System.out.printf("blocked: %s\n", position.getBlocked());
            System.out.printf("positionUid: %s\n", position.getPositionUid());
            System.out.printf("instrumentType: %s\n", position.getInstrumentType());
            System.out.printf("balance: %s\n", position.getBalance());
            System.out.printf("instrumentUid: %s\n", position.getInstrumentUid());
        }
    }

    @Test
    public void testMarketDataService() throws IOException, AbstractException {
        var tinkoffBroker = getTinkoffSandbox();
        var share = getTestShare();
        var marketDataService = tinkoffBroker.getMarketDataService();
        var techAnalysisService = tinkoffBroker.getTechAnalysisService();

        System.out.println("\nTesting method getLastPrices: \n");
        var lastPricesResponse = marketDataService.getLastPrices(GetLastPricesRequest.of(share.getUid()));

        for (var lastPrice : lastPricesResponse.getLastPrices()) {
            System.out.printf("price: %s\n", lastPrice.getPrice());
            System.out.printf("instrumentUid: %s\n", lastPrice.getInstrumentUid());
            System.out.printf("time: %s\n", lastPrice.getTime());
        }

        System.out.println("\nTesting method getCandles: \n");
        var getCandlesResponse = marketDataService.getCandles(
                GetCandlesRequest.of(
                        Instant.parse("2023-11-20T12:00:00.00Z"),
                        Instant.parse("2024-01-01T12:00:00.00Z"),
                        CandleInterval.DAY,
                        share.getUid()
                )
        );

        for (var candle : getCandlesResponse.getCandles()) {
            System.out.printf("close: %s\n", candle.getClose());
            System.out.printf("open: %s\n", candle.getOpen());
            System.out.printf("high: %s\n", candle.getHigh());
            System.out.printf("low: %s\n", candle.getLow());
            System.out.printf("volume: %s\n", candle.getVolume());
            System.out.printf("time: %s\n", candle.getTime());
            System.out.println();
        }

        var getTechAnalysisResponse = techAnalysisService.getTechAnalysis(
                new GetTechAnalysisRequest()
                        .setFrom(Instant.parse("2023-11-20T12:00:00.00Z"))
                        .setTo(Instant.parse("2023-11-30T12:00:00.00Z"))
                        .setInstrumentUid(share.getUid())
                        .setInterval(IndicatorInterval.HOUR_4)
                        .setPriceType(com.siberalt.singularity.broker.contract.service.market.request.PriceType.AVG)
                        .setIndicatorType(IndicatorType.EMA)
                        .setLength(1)
        );

        for (var techAnalysisItem : getTechAnalysisResponse.getTechnicalIndicators()) {
            System.out.println(techAnalysisItem);
            System.out.println();
        }
    }

    @Test
    public void testExceptionsHandling() throws IOException, AbstractException {
        TinkoffSandboxBroker finalTinkoffBroker = new TinkoffSandboxBroker(
                getConfiguration().get("sandboxToken") + "123"
        );
        Assertions.assertThrows(
                PermissionDeniedException.class, () -> finalTinkoffBroker.getUserService().getAccounts(null)
        );

        var tinkoffBroker = getTinkoffSandbox();
        var orderService = tinkoffBroker.getOrderService();
        var share = getTestShare();
        var caught = false;

        String accountId = openTestAccount(
            "TestAccount", MoneyValue.newBuilder().setCurrency("RUB").setUnits(120).build()
        );

        try {
            orderService.post(
                    new PostOrderRequest()
                            .setAccountId(accountId)
                            .setDirection(OrderDirection.BUY)
                            .setInstrumentId(share.getUid())
                            .setOrderType(OrderType.BEST_PRICE)
                            .setQuantity(10000)
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

    protected boolean isOrderExists(String accountId, String orderId) throws IOException, AbstractException {
        return getTinkoffSandbox()
                .getOrderService()
                .get(GetOrdersRequest.of(accountId))
                .getOrders()
                .stream()
                .anyMatch(x -> Objects.equals(x.getOrderId(), orderId));
    }

    protected void assertInstrumentBalance(String accountId, String instrumentUid, long expectedBalance) throws IOException, AbstractException {
        var instrumentPosition = getTinkoffSandbox()
                .getOperationsService()
                .getPositions(GetPositionsRequest.of(accountId))
                .getSecurities()
                .stream()
                .filter(x -> Objects.equals(x.getInstrumentUid(), instrumentUid))
                .findFirst()
                .orElse(null);

        if (null == instrumentPosition && expectedBalance > 0) {
            Assertions.fail("Instrument position should be present. Expected balance: " + expectedBalance);
        }

        Assertions.assertEquals(instrumentPosition == null ? 0 : instrumentPosition.getBalance(), expectedBalance);
    }

    protected Instrument getTestShare() throws IOException, AbstractException {
        if (null == testShare) {
            var response = getTinkoffSandbox().getInstrumentService()
                    .get(GetRequest.of((String) getConfiguration().get("shareIsin")));
            testShare = response.getInstrument();
        }

        return testShare;
    }

    protected String openTestAccount(String name) throws IOException, AbstractException {
        return openTestAccount(name, MoneyValue.newBuilder().setCurrency("RUB").setUnits(120000).build());
    }

    protected String openTestAccount(String name, MoneyValue startBalance) throws IOException, AbstractException {
        var tinkoffBroker = getTinkoffSandbox();
        var responseAccounts = tinkoffBroker.getUserService().getAccounts(null);
        TinkoffSandboxService sandboxService = tinkoffBroker.getSandboxService();

        if (!responseAccounts.getAccounts().isEmpty()) {
            responseAccounts
                    .getAccounts()
                    .stream()
                    .map(Account::getId)
                    .forEach(sandboxService::closeAccount);
        }

        testAccountId = sandboxService.openAccount(name);
        sandboxService.payIn(testAccountId, MoneyValueTranslator.toContract(startBalance));

        return testAccountId;
    }

    protected TinkoffSandboxBroker getTinkoffSandbox() throws IOException {
        if (null == tinkoffBroker) {
            configuration = getConfiguration();
            tinkoffBroker = new TinkoffSandboxBroker((String) configuration.get("sandboxToken"));
        }

        return tinkoffBroker;
    }

    protected ConfigInterface getConfiguration() throws IOException {
        if (null == configuration) {
            configuration = new YamlConfig(
                    Files.newInputStream(Paths.get("src/test/resources/broker/tinkoff/test-settings.yaml"))
            );
        }

        return configuration;
    }
}
