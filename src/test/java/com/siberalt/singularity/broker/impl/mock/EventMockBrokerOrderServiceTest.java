package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.InvalidRequestException;
import com.siberalt.singularity.broker.contract.service.order.request.CancelOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetOrderStateRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetOrdersRequest;
import com.siberalt.singularity.broker.contract.service.order.response.CancelOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.service.order.response.OrderState;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.strategy.context.Clock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * The order service as {@link EventMockBroker} builds it, plus what only this configuration can do:
 * park an order until the market reaches its price, and cancel one while it waits.
 */
public class EventMockBrokerOrderServiceTest extends AbstractMockOrderServiceTest {
    protected EventObserver eventObserver;

    @Override
    MockBroker createBroker(
        ReadCandleRepository candleStorage,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        Clock clock
    ) {
        EventMockBroker broker = new EventMockBroker(
            candleStorage,
            instrumentStorage,
            orderRepository,
            operationRepository,
            clock
        );
        eventObserver = new EventObserver();
        broker.getPendingOrderHandler().observeEventsBy(eventObserver);

        return broker;
    }

    @Test
    public void testBuyLimitParksUntilMarketReachesThePrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        Candle buySignalCandle = createCandle(
            currentTime, 10, 15, 5, 9, 100
        );

        addMoney(validCandle.open().multiply(100));
        when(candleStorage.findByOpenPrice(any())).thenReturn(List.of(buySignalCandle));

        assertBuyParked(validCandle, 10, Quotation.of(9));
    }

    @Test
    public void testBuyLimitParksWhenMarketNeverReachesThePrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        Candle expirationCandle = createCandle(
            lifeTimeEnd(), 10, 15, 5, 12, 100
        );

        addMoney(validCandle.open().multiply(100));
        stubLastCandleOfLifeTime(expirationCandle);

        // findByOpenPrice finds no candle crossing the limit, so the order is scheduled to expire
        // against the last candle of its lifetime rather than to fill. It is still parked - and
        // still holds the money - until that moment arrives.
        assertBuyParked(validCandle, 10, Quotation.of(9));
    }

    @Test
    public void testSellLimitParksUntilMarketReachesThePrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        Candle sellSignalCandle = createCandle(
            currentTime, 10, 15, 5, 12, 100
        );

        addInstruments(80);
        when(candleStorage.findByOpenPrice(any())).thenReturn(List.of(sellSignalCandle));

        assertSellParked(validCandle, 10, Quotation.of(11));
    }

    @Test
    public void testSellLimitParksWhenMarketNeverReachesThePrice() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        Candle expirationCandle = createCandle(
            lifeTimeEnd(), 10, 15, 5, 9, 100
        );

        addInstruments(80);
        stubLastCandleOfLifeTime(expirationCandle);

        assertSellParked(validCandle, 10, Quotation.of(11));
    }

    protected SimulatedPendingOrderHandler pendingOrderHandler() {
        return ((EventMockBroker) broker).getPendingOrderHandler();
    }

    protected Instant lifeTimeEnd() {
        return currentTime.plus(pendingOrderHandler().getLimitOrderLifeTime());
    }

    /**
     * The candle the handler falls back to when nothing crosses the limit price - it schedules the
     * order to be rejected at that moment.
     */
    protected void stubLastCandleOfLifeTime(Candle expirationCandle) {
        when(candleStorage.findBeforeOrEqual(config.getInstrument().getUid(), lifeTimeEnd(), 1))
            .thenReturn(List.of(expirationCandle));
    }

    @Test
    public void testCancelOrder() throws AbstractException {
        Candle buyCandle = createCandle(
            Instant.parse("2021-12-15T15:00:00Z"), 1000, 110, 90, 100, 100
        );
        Candle expirationCandle = createCandle(
            Instant.parse("2021-12-15T15:10:00Z"), 10, 15, 6, 10, 100
        );
        when(candleStorage.findBeforeOrEqual(any(), any(), eq(1L)))
            .thenReturn(List.of(expirationCandle));

        addMoney(Quotation.of(20000D));

        assertCancelOrder(assertBuyParked(buyCandle, 10, Quotation.of(99)));

        Candle buySignalCandle = createCandle(
            Instant.parse("2021-12-15T15:20:00Z"), 10, 15, 6, 10, 100
        );
        when(candleStorage.findByOpenPrice(any()))
            .thenReturn(List.of(buySignalCandle));
        Candle sellCandle = createCandle(
            Instant.parse("2021-12-15T15:00:00Z"), 1000, 110, 90, 100, 100
        );

        addInstruments(10);
        assertCancelOrder(assertSellParked(sellCandle, 10, Quotation.of(110)));
    }

    protected void assertCancelOrder(PostOrderResponse postResponse) throws AbstractException {
        CancelOrderResponse cancelResponse = orderService.cancel(
            new CancelOrderRequest()
                .setOrderId(postResponse.getOrderId())
                .setAccountId(testAccount.getId())
        );

        assertNotNull(cancelResponse);

        // A cancelled order is kept, so getState() reports CANCELLED instead of pretending the
        // order never existed.
        OrderState cancelledState = orderService.getState(
            new GetOrderStateRequest()
                .setOrderId(postResponse.getOrderId())
                .setAccountId(testAccount.getId())
        );

        assertEquals(ExecutionStatus.CANCELLED, cancelledState.getExecutionStatus());
        assertEquals(0, cancelledState.getLotsExecuted());

        Optional<Operation> cancelOperation = operationRepository.getByAccountId(testAccount.getId(), TimeRange.MAX)
            .stream()
            .filter(operation -> operation.state() == OperationState.CANCELED)
            .filter(operation -> operation.quantity() == postResponse.getLotsRequested())
            .filter(operation -> operation.direction().isBuy() == postResponse.getDirection().isBuy())
            .findFirst();

        assertTrue(cancelOperation.isPresent());
        assertEquals(0, cancelOperation.get().quantityDone());
        assertEquals(Quotation.ZERO, cancelOperation.get().payment());

        // The order is still there, just no longer active, so a repeat cancel is reported as a bad
        // request against a known order rather than as a missing one.
        assertThrowsWithErrorCode(
            InvalidRequestException.class,
            ErrorCode.CANCEL_ORDER_ERROR,
            () -> orderService.cancel(
                new CancelOrderRequest()
                    .setOrderId(postResponse.getOrderId())
                    .setAccountId(testAccount.getId())
            )
        );
    }

    /**
     * The other half of "get orders", which only this broker can reach: an order still waiting for
     * the market is exactly what the call is supposed to return.
     */
    @Test
    public void testGetReportsParkedOrder() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        Candle buySignalCandle = createCandle(
            currentTime, 10, 15, 5, 9, 100
        );

        addMoney(validCandle.open().multiply(100));
        when(candleStorage.findByOpenPrice(any())).thenReturn(List.of(buySignalCandle));

        PostOrderResponse parked = assertBuyParked(validCandle, 10, Quotation.of(9));

        List<OrderState> activeOrders = orderService
            .get(GetOrdersRequest.of(testAccount.getId()))
            .getOrders();

        assertEquals(1, activeOrders.size());
        assertEquals(parked.getOrderId(), activeOrders.getFirst().getOrderId());
        assertEquals(ExecutionStatus.NEW, activeOrders.getFirst().getExecutionStatus());
    }
}
