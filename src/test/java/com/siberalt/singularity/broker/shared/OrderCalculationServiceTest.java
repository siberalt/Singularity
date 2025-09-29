package com.siberalt.singularity.broker.shared;

import com.siberalt.singularity.broker.contract.execution.Broker;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetResponse;
import com.siberalt.singularity.broker.contract.service.market.MarketDataService;
import com.siberalt.singularity.broker.contract.service.market.response.GetCurrentPriceResponse;
import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import com.siberalt.singularity.broker.contract.service.operation.response.GetPositionsResponse;
import com.siberalt.singularity.broker.contract.service.order.OrderService;
import com.siberalt.singularity.broker.contract.service.order.request.CalculateRequest;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.service.order.request.PostOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.response.CalculateResponse;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.shared.dto.BuyRequest;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OrderCalculationServiceTest {
    private OrderService orderServiceMock;
    private OrderCalculationService orderCalculationService;
    private Broker brokerMock;
    private InstrumentService instrumentServiceMock;
    private OperationsService operationsServiceMock;
    private MarketDataService marketDataServiceMock;

    @BeforeEach
    void setUp() {
        brokerMock = mock(Broker.class);
        instrumentServiceMock = mock(InstrumentService.class);
        operationsServiceMock = mock(OperationsService.class);
        marketDataServiceMock = mock(MarketDataService.class);
        orderServiceMock = mock(OrderService.class);

        when(brokerMock.getInstrumentService()).thenReturn(instrumentServiceMock);
        when(brokerMock.getOperationsService()).thenReturn(operationsServiceMock);
        when(brokerMock.getMarketDataService()).thenReturn(marketDataServiceMock);
        when(brokerMock.getOrderService()).thenReturn(orderServiceMock);

        orderCalculationService = new OrderCalculationService(0);
    }

    @Test
    void calculatesPossibleBuyQuantityWithValidInputs() throws Exception {
        Quotation balance = Quotation.of("1000");
        Quotation instrumentPrice = Quotation.of("100");

        when(orderServiceMock.calculate(any())).thenReturn(new CalculateResponse(
            "instrumentId",
            Quotation.of("1000"),
            instrumentPrice,
            10,
            List.of() // Assuming no transactions for simplicity
        ));

        long result = orderCalculationService.calculatePossibleBuyQuantity(
            orderServiceMock,
            balance,
            instrumentPrice,
            new BuyRequest("accountId", "instrumentId", OrderType.MARKET)
        );

        assertEquals(10, result);
    }

    @Test
    void calculatesPossibleBuyQuantityWhenBalanceIsZero() throws Exception {
        Quotation balance = Quotation.of("0");
        Quotation instrumentPrice = Quotation.of("100");

        long result = orderCalculationService.calculatePossibleBuyQuantity(
            orderServiceMock,
            balance,
            instrumentPrice,
            new BuyRequest("accountId", "instrumentId", OrderType.MARKET)
        );

        assertEquals(0, result);
    }

    @Test
    void calculatesPossibleBuyQuantityWhenPriceIsZero() {
        Quotation balance = Quotation.of("1000");
        Quotation instrumentPrice = Quotation.of("0");

        assertThrows(
            ArithmeticException.class,
            () -> orderCalculationService.calculatePossibleBuyQuantity(
                orderServiceMock,
                balance,
                instrumentPrice,
                new BuyRequest("accountId", "instrumentId", OrderType.MARKET)
            )
        );
    }

    @Test
    void calculatesPossibleBuyQuantityWhenTotalPriceExceedsBalance() throws Exception {
        Quotation balance = Quotation.of("1000");
        Quotation instrumentPrice = Quotation.of("100");

        when(orderServiceMock.calculate(any()))
            .thenAnswer(invocation -> mockCalculateResponse(invocation, instrumentPrice));

        long result = orderCalculationService.calculatePossibleBuyQuantity(
            orderServiceMock,
            balance,
            instrumentPrice,
            new BuyRequest("accountId", "instrumentId", OrderType.MARKET)
        );

        assertEquals(9, result);
    }

    @Test
    void calculatesPossibleBuyQuantityWithValidInputsForBroker() throws Exception {
        when(instrumentServiceMock.get(any())).thenReturn(instrumentResponse("USD"));
        when(operationsServiceMock.getPositions(any()))
            .thenReturn(positionsResponse("USD", Quotation.of(1000)));
        Quotation pricePerOne = Quotation.of(100);
        when(marketDataServiceMock.getCurrentPrice(any()))
            .thenReturn(priceResponse("instrumentId", pricePerOne));
        when(orderServiceMock.calculate(any()))
            .thenAnswer(invocation -> mockCalculateResponse(invocation, pricePerOne));

        long result = orderCalculationService.calculatePossibleBuyQuantity(
            brokerMock,
            new BuyRequest(
                "accountId",
                "instrumentId",
                OrderType.MARKET
            )
        );

        assertEquals(9, result);
    }

    @Test
    void calculatesPossibleBuyQuantityWhenBalanceIsZeroForBroker() throws Exception {
        when(instrumentServiceMock.get(any())).thenReturn(instrumentResponse("USD"));
        when(operationsServiceMock.getPositions(any()))
            .thenReturn(positionsResponse("USD", Quotation.of(0)));
        when(marketDataServiceMock.getCurrentPrice(any()))
            .thenReturn(priceResponse("instrumentId", Quotation.of(100)));

        long result = orderCalculationService.calculatePossibleBuyQuantity(
            brokerMock,
            new BuyRequest(
                "accountId",
                "instrumentId",
                OrderType.MARKET
            )
        );

        assertEquals(0, result);
    }

    @Test
    void calculatesPossibleBuyQuantityWhenPriceIsZeroForBroker() throws AbstractException {
        when(instrumentServiceMock.get(any())).thenReturn(instrumentResponse("USD"));
        when(operationsServiceMock.getPositions(any()))
            .thenReturn(positionsResponse("USD", Quotation.of("1000")));
        when(marketDataServiceMock.getCurrentPrice(any()))
            .thenReturn(priceResponse("instrumentId", Quotation.of("0")));

        assertThrows(
            ArithmeticException.class,
            () -> orderCalculationService.calculatePossibleBuyQuantity(
                brokerMock,
                new BuyRequest(
                    "accountId",
                    "instrumentId",
                    OrderType.MARKET
                )
            )
        );
    }

    @Test
    void calculatesPossibleBuyQuantityWhenTotalPriceExceedsBalanceForBroker() throws Exception {
        when(instrumentServiceMock.get(any())).thenReturn(instrumentResponse("USD"));
        when(operationsServiceMock.getPositions(any()))
            .thenReturn(positionsResponse("USD", Quotation.of("1000")));
        when(marketDataServiceMock.getCurrentPrice(any()))
            .thenReturn(priceResponse("instrumentId", Quotation.of("100")));
        when(orderServiceMock.calculate(any()))
            .thenAnswer(invocation -> mockCalculateResponse(invocation, Quotation.of("2000")));

        long result = orderCalculationService.calculatePossibleBuyQuantity(
            brokerMock,
            new BuyRequest(
                "accountId",
                "instrumentId",
                OrderType.MARKET
            )
        );

        assertEquals(0, result);
    }

    @Test
    void calculatePossibleBuyQuantityWithValidInputs() throws Exception {
        Quotation limit = Quotation.of("1000");
        Quotation instrumentPrice = Quotation.of("100");
        BuyRequest request = new BuyRequest("accountId", "instrumentId", OrderType.MARKET);

        when(marketDataServiceMock.getCurrentPrice(any()))
            .thenReturn(new GetCurrentPriceResponse().setPrice(instrumentPrice));
        when(orderServiceMock.calculate(any())).thenReturn(new CalculateResponse(
            "instrumentId",
            Quotation.of("1000"),
            instrumentPrice,
            10,
            List.of() // Assuming no transactions for simplicity
        ));

        long result = orderCalculationService.calculatePossibleBuyQuantity(brokerMock, limit, request);

        assertEquals(10, result);
    }

    @Test
    void calculatePossibleBuyQuantityWithValidInputsAndExtraRatio() throws Exception {
        Quotation limit = Quotation.of(1000);
        Quotation instrumentPrice = Quotation.of(100);
        BuyRequest request = new BuyRequest("accountId", "instrumentId", OrderType.MARKET);

        when(marketDataServiceMock.getCurrentPrice(any()))
            .thenReturn(new GetCurrentPriceResponse().setPrice(instrumentPrice));
        when(orderServiceMock.calculate(any()))
            .thenReturn(new CalculateResponse(
                "instrumentId",
                instrumentPrice.multiply(9).multiply(1.03), // Simulating commission of 3%
                instrumentPrice,
                9,
                List.of() // Assuming no transactions for simplicity
            ));

        OrderCalculationService serviceWithExtraRatio = new OrderCalculationService(0.03); // 3% extra ratio

        long result = serviceWithExtraRatio.calculatePossibleBuyQuantity(brokerMock, limit, request);

        assertEquals(9, result); // Expecting 9 due to the extra ratio
    }

    private Object mockCalculateResponse(InvocationOnMock invocation, Quotation instrumentPrice) {
        CalculateRequest calculateRequest = invocation.getArgument(0);
        PostOrderRequest request = calculateRequest.getPostOrderRequest();
        long quantity = request.getQuantity();
        Quotation initialPrice = instrumentPrice.multiply(quantity);
        Quotation totalBalanceChange = initialPrice.add(initialPrice.multiply(0.03)); // Simulating commission of 3%

        return new CalculateResponse(
            "instrumentId",
            totalBalanceChange,
            instrumentPrice,
            quantity,
            List.of() // Assuming no transactions for simplicity
        );
    }

    private GetPositionsResponse positionsResponse(String currency, Quotation balance) {
        return new GetPositionsResponse().setMoney(List.of(Money.of(currency, balance)));
    }

    private GetResponse instrumentResponse(String currency) {
        GetResponse response = new GetResponse();
        Instrument instrument = new Instrument();
        instrument.setCurrency(currency);
        response.setInstrument(instrument);
        return response;
    }

    private GetCurrentPriceResponse priceResponse(String instrumentId, Quotation price) {
        return new GetCurrentPriceResponse().setPrice(price).setInstrumentUid(instrumentId);
    }
}
