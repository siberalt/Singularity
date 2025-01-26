package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.InvalidRequestException;
import com.siberalt.singularity.broker.contract.service.order.request.CancelOrderRequest;
import com.siberalt.singularity.broker.contract.service.order.request.GetOrderStateRequest;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.service.order.response.CancelOrderResponse;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.service.order.response.OrderState;
import com.siberalt.singularity.broker.contract.service.order.response.PostOrderResponse;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.simulation.EventObserver;
import com.siberalt.singularity.simulation.shared.instrument.InstrumentStorageInterface;
import com.siberalt.singularity.simulation.shared.market.candle.Candle;
import com.siberalt.singularity.simulation.shared.market.candle.CandleStorageInterface;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class EventSimulatedOrderServiceTest extends MockOrderServiceTest {
    protected EventObserver eventObserver;

    @Override
    MockBroker createBroker(CandleStorageInterface candleStorage, InstrumentStorageInterface instrumentStorage) {
        var broker = new EventMockBroker(candleStorage, instrumentStorage);
        eventObserver = new EventObserver();
        broker.getOrderService().observeEventsBy(eventObserver);

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
        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.of(expirationCandle));

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

        OrderState state = orderService.getState(
            new GetOrderStateRequest()
                .setOrderId(postResponse.getOrderId())
                .setAccountId(testAccount.getId())
        );
        assertEquals(ExecutionStatus.CANCELLED, state.getExecutionStatus());
        assertEquals(10, state.getLotsRequested());
        assertEquals(0, state.getLotsExecuted());
        assertEquals(Quotation.of(0), state.getTotalPrice().getQuotation());
        assertEquals(Quotation.of(0), state.getExecutedOrderPrice().getQuotation());

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
            assertBuyOrder(testCandles[0], OrderType.MARKET, 1, testCandles[0].getOpenPrice()),
            assertBuyOrder(testCandles[1], OrderType.LIMIT, 12, testCandles[1].getOpenPrice()),
            assertBuyOrder(testCandles[3], OrderType.BEST_PRICE, 12),
            assertSellOrder(testCandles[4], OrderType.MARKET, 10, testCandles[3].getOpenPrice()),
            assertSellOrder(testCandles[5], OrderType.LIMIT, 12, testCandles[4].getOpenPrice()),
            assertBuyOrder(testCandles[7], OrderType.BEST_PRICE, 12),
        };
    }
}
