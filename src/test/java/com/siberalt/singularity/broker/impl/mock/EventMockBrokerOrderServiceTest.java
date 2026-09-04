package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.InvalidRequestException;
import com.siberalt.singularity.broker.contract.service.order.request.CancelOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetOrderStateRequest;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.service.order.response.OrderState;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.service.order.response.CancelOrderResponse;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class EventMockBrokerOrderServiceTest extends MockOrderServiceTest {
    protected EventObserver eventObserver;

    @Override
    MockBroker createBroker(
        ReadCandleRepository candleStorage,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        Clock clock
    ) {
        var broker = new EventMockBroker(candleStorage, instrumentStorage, orderRepository, operationRepository, clock);
        eventObserver = new EventObserver();
        broker.getPendingOrderHandler().observeEventsBy(eventObserver);

        return broker;
    }

    @Test
    public void testCancelOrder() throws AbstractException {
        // Test on order expiration
        Candle buyCandle = createCandle(
            Instant.parse("2021-12-15T15:00:00Z"), 1000, 110, 90, 100, 100
        );
        Candle expirationCandle = createCandle(
            Instant.parse("2021-12-15T15:10:00Z"), 10, 15, 6, 10, 100
        );
        when(candleStorage.findBeforeOrEqual(any(), any(), eq(1L)))
            .thenReturn(List.of(expirationCandle));

        addMoney(Quotation.of(20000D));

        PostOrderResponse postResponse = assertBuyOrder(buyCandle, OrderType.LIMIT, 10, Quotation.of(99));
        assertCancelOrder(postResponse);

        // Test on order limit execution
        Candle buySignalCandle = createCandle(
            Instant.parse("2021-12-15T15:20:00Z"), 10, 15, 6, 10, 100
        );
        when(candleStorage.findByOpenPrice(any()))
            .thenReturn(List.of(buySignalCandle));
        Candle sellCandle = createCandle(
            Instant.parse("2021-12-15T15:00:00Z"), 1000, 110, 90, 100, 100
        );

        addInstruments(10);
        postResponse = assertSellOrder(sellCandle, OrderType.LIMIT, 10, Quotation.of(110));
        assertCancelOrder(postResponse);
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

    @Override
    protected PostOrderResponse[] requestsForTestGet() throws AbstractException {
        Instant currentTime = Instant.parse("2021-12-15T15:00:00Z");

        Instant[] times = {
            currentTime,
            currentTime.plus(1, ChronoUnit.HOURS),
            currentTime.plus(20, ChronoUnit.MINUTES),
            currentTime.plus(5, ChronoUnit.SECONDS),
            currentTime.plus(6, ChronoUnit.MILLIS),
            currentTime.plus(3, ChronoUnit.DAYS),
            currentTime.plus(10, ChronoUnit.MINUTES),
            currentTime.plus(23, ChronoUnit.MINUTES),
        };

        Candle[] testCandles = {
            createCandle(
                times[0], 11, 16, 10, 10, 100
            ),
            createCandle(
                times[1], 12, 12, 10, 12, 200
            ),
            createCandle(
                times[2], 16, 17, 6, 15, 150
            ),
            createCandle(
                times[3], 11, 20, 7, 16, 1000
            ),
            createCandle(
                times[4], 12, 18, 9, 14, 1
            ),
            createCandle(
                times[5], 12, 18, 9, 14, 1
            ),
            createCandle(
                times[6], 11, 15, 1, 12, 10
            ),
            createCandle(
                times[7], 10, 10, 10, 10, 78
            ),
        };

        when(candleStorage.findByOpenPrice(any()))
            .thenReturn(List.of(testCandles[2]))
            .thenReturn(List.of(testCandles[5]));

        addMoney(Quotation.of(1000));

        return new PostOrderResponse[] {
            assertBuyOrder(testCandles[0], OrderType.MARKET, 1, testCandles[0].open()),
            assertBuyOrder(testCandles[1], OrderType.LIMIT, 12, testCandles[1].open()),
            assertBuyOrder(testCandles[3], OrderType.BEST_PRICE, 12),
            assertSellOrder(testCandles[4], OrderType.MARKET, 10, testCandles[3].open()),
            assertSellOrder(testCandles[5], OrderType.LIMIT, 12, testCandles[4].open()),
            assertBuyOrder(testCandles[7], OrderType.BEST_PRICE, 12),
        };
    }
}
