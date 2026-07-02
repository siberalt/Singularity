package com.siberalt.singularity.strategy.market.position;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.ReadOrderRepository;
import com.siberalt.singularity.shared.TimePointRange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BaseEntryPriceCalculator Тесты")
class BaseEntryPriceCalculatorTest {
    private ReadOrderRepository orderRepository;
    private BaseEntryPriceCalculator calculator;

    @BeforeEach
    void setUp() {
        orderRepository = mock(ReadOrderRepository.class);
        calculator = new BaseEntryPriceCalculator(orderRepository);
    }

    @AfterEach
    void tearDown() {
        BaseEntryPriceCalculator.clearCache();
    }

    // ==================== Тесты базовой агрегации ====================

    @Nested
    @DisplayName("Базовая агрегация ордеров")
    class BasicAggregationTests {

        @Test
        @DisplayName("Возвращает EMPTY состояние при отсутствии ордеров")
        void testEmptyStateWhenNoOrders() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";
            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Collections.emptyList());

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertTrue(state.isEmpty());
            assertEquals(EntryPrice.EMPTY, state);
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
        }

        @Test
        @DisplayName("Агрегирует ордера при первом вызове (кэш пуст)")
        void testAggregateFromOrdersOnFirstCall() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Order buyOrder1 = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order buyOrder2 = createOrder(50, 12.0, OrderDirection.BUY, ExecutionStatus.FILL, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder1, buyOrder2));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(150, state.quantity());
            assertEquals(10.666666666666666, state.averagePrice().toDouble(), 0.0001);
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
        }

        @Test
        @DisplayName("Игнорирует неисполненные ордера при агрегации")
        void testIgnoresUnfilledOrders() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Order filledOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order unfilledOrder = createOrder(50, 15.0, OrderDirection.BUY, ExecutionStatus.CANCELLED, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(filledOrder, unfilledOrder));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(100, state.quantity());
            assertEquals(10.0, state.averagePrice().toDouble());
            assertEquals(new TimePointRange(new TimePoint(time1)), state.timePointRange());
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
        }

        @Test
        @DisplayName("Тест с несколькими покупками подряд")
        void testMultipleBuysInSequence() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");
            Instant time3 = Instant.parse("2024-10-30T18:00:00Z");

            Order buyOrder1 = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order buyOrder2 = createOrder(100, 12.0, OrderDirection.BUY, ExecutionStatus.FILL, time2);
            Order buyOrder3 = createOrder(100, 14.0, OrderDirection.BUY, ExecutionStatus.FILL, time3);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder1, buyOrder2, buyOrder3));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            TimePointRange expectedRange = new TimePointRange(
                new TimePoint(time1),
                new TimePoint(time3)
            );
            assertEquals(300, state.quantity());
            assertEquals(12.0, state.averagePrice().toDouble());
            assertEquals(expectedRange, state.timePointRange());
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
        }
    }

    // ==================== Тесты обработки позиций ====================

    @Nested
    @DisplayName("Обработка позиций (покупки/продажи)")
    class PositionHandlingTests {

        @Test
        @DisplayName("Обрабатывает покупку и продажу (выход из позиции)")
        void testBuyThenSellReducesPosition() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Order buyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order sellOrder = createOrder(50, 12.0, OrderDirection.SELL, ExecutionStatus.FILL, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder, sellOrder));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(50, state.quantity());
            assertEquals(10.0, state.averagePrice().toDouble());
        }

        @Test
        @DisplayName("Обрабатывает покупку и продажу с пересечением (реверс позиции)")
        void testBuyThenSellReversePosition() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Order buyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order sellOrder = createOrder(150, 12.0, OrderDirection.SELL, ExecutionStatus.FILL, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder, sellOrder));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(-50, state.quantity());
            assertEquals(12.0, state.averagePrice().toDouble());
            assertEquals(new TimePointRange(new TimePoint(time2)), state.timePointRange());
        }

        @Test
        @DisplayName("Возвращает пустое состояние при нулевой позиции")
        void testEmptyStateWhenPositionClosed() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Order buyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order sellOrder = createOrder(100, 10.0, OrderDirection.SELL, ExecutionStatus.FILL, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder, sellOrder));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertTrue(state.isEmpty());
        }

        @Test
        @DisplayName("Тест с несколькими продажами подряд")
        void testMultipleSellsInSequence() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");
            Instant time3 = Instant.parse("2024-09-30T23:00:00Z");

            Order sellOrder1 = createOrder(100, 10.0, OrderDirection.SELL, ExecutionStatus.FILL, time1);
            Order sellOrder2 = createOrder(100, 12.0, OrderDirection.SELL, ExecutionStatus.FILL, time2);
            Order sellOrder3 = createOrder(100, 14.0, OrderDirection.SELL, ExecutionStatus.FILL, time3);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(sellOrder1, sellOrder2, sellOrder3));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            TimePointRange expectedRange = new TimePointRange(
                new TimePoint(time1),
                new TimePoint(time3)
            );
            assertEquals(-300, state.quantity());
            assertEquals(12.0, state.averagePrice().toDouble());
            assertEquals(expectedRange, state.timePointRange());
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
        }

        @Test
        @DisplayName("Тест частичного закрытия позиции")
        void testPartialPositionClosure() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Order buyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order sellOrder = createOrder(60, 12.0, OrderDirection.SELL, ExecutionStatus.FILL, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder, sellOrder));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(40, state.quantity());
            assertEquals(10.0, state.averagePrice().toDouble());
            assertEquals(new TimePointRange(new TimePoint(time1)), state.timePointRange());
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
        }

        @Test
        @DisplayName("Тест с нулевым количеством (отмена)")
        void testZeroQuantityWhenEqualBuyAndSell() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Order buyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order sellOrder = createOrder(100, 15.0, OrderDirection.SELL, ExecutionStatus.FILL, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder, sellOrder));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertTrue(state.isEmpty());
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
        }
    }

    // ==================== Тесты TimePointRange ====================

    @Nested
    @DisplayName("TimePointRange по заявкам")
    class TimePointRangeTests {

        @Test
        @DisplayName("TimePointRange пустой при отсутствии ордеров")
        void testEmptyTimePointRangeWhenNoOrders() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";
            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Collections.emptyList());

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertTrue(state.timePointRange().isEmpty());
        }

        @Test
        @DisplayName("TimePointRange формируется по времени выполнения ордеров")
        void testTimePointRangeFromOrders() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";
            Instant time1 = Instant.parse("2025-01-01T10:00:00Z");
            Instant time2 = Instant.parse("2025-01-01T11:00:00Z");

            Order buyOrder1 = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order buyOrder2 = createOrder(50, 12.0, OrderDirection.BUY, ExecutionStatus.FILL, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder1, buyOrder2));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(time1, state.timePointRange().fromTime());
            assertEquals(time2, state.timePointRange().toTime());
        }

        @Test
        @DisplayName("TimePointRange объединяется при обновлении из кэша")
        void testTimePointRangeUnionWithCache() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant oldBuyOrderTime = Instant.parse("2025-01-01T10:00:00Z");
            Instant newBuyOrderTime = Instant.parse("2025-01-01T15:00:00Z");

            Order oldBuyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, oldBuyOrderTime);
            Order newBuyOrder = createOrder(50, 12.0, OrderDirection.BUY, ExecutionStatus.FILL, newBuyOrderTime);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(List.of(oldBuyOrder));

            EntryPrice entryPrice1 = calculator.calculate(accountId, instrumentUid);
            assertEquals(oldBuyOrderTime, entryPrice1.timePointRange().fromTime());
            assertEquals(oldBuyOrderTime, entryPrice1.timePointRange().toTime());

            when(orderRepository.getByAccountIdAndInstrumentUidAfterTime(eq(accountId), eq(instrumentUid), eq(oldBuyOrderTime)))
                .thenReturn(List.of(newBuyOrder));

            EntryPrice entryPrice = calculator.calculate(accountId, instrumentUid);

            // Время объединяется: от старой заявки до новой
            assertEquals(oldBuyOrderTime, entryPrice.timePointRange().fromTime());
            assertEquals(newBuyOrderTime, entryPrice.timePointRange().toTime());
        }

        @Test
        @DisplayName("TimePointRange обновляется при последовательных покупках")
        void testTimePointRangeWithSequentialBuys() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";
            Instant time1 = Instant.parse("2025-01-01T09:00:00Z");
            Instant time2 = Instant.parse("2025-01-01T10:00:00Z");
            Instant time3 = Instant.parse("2025-01-01T11:00:00Z");

            Order buyOrder1 = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order buyOrder2 = createOrder(100, 12.0, OrderDirection.BUY, ExecutionStatus.FILL, time2);
            Order buyOrder3 = createOrder(100, 14.0, OrderDirection.BUY, ExecutionStatus.FILL, time3);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder1, buyOrder2, buyOrder3));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(time1, state.timePointRange().fromTime());
            assertEquals(time3, state.timePointRange().toTime());
            assertEquals(300, state.quantity());
        }

        @Test
        @DisplayName("TimePointRange охватывает весь период при частичной продаже (без реверса)")
        void testTimePointRangeWithPartialSell() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";
            Instant time1 = Instant.parse("2025-01-01T09:00:00Z");
            Instant time2 = Instant.parse("2025-01-01T12:00:00Z");

            Order buyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order sellOrder = createOrder(50, 12.0, OrderDirection.SELL, ExecutionStatus.FILL, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder, sellOrder));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            // Без реверса время охватывает весь период
            assertEquals(time1, state.timePointRange().fromTime());
            assertEquals(time1, state.timePointRange().toTime());
            assertEquals(50, state.quantity());
        }

        @Test
        @DisplayName("TimePointRange при реверсе позиции (сброс времени на момент входа в шорт)")
        void testTimePointRangeWithPositionReverse() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";
            Instant time1 = Instant.parse("2025-01-01T08:00:00Z");
            Instant time2 = Instant.parse("2025-01-01T14:00:00Z");

            Order buyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, time1);
            Order sellOrder = createOrder(150, 12.0, OrderDirection.SELL, ExecutionStatus.FILL, time2);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder, sellOrder));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            // При реверсе позиции время должно сброситься на момент входа в шорт
            assertEquals(time2, state.timePointRange().fromTime());
            assertEquals(time2, state.timePointRange().toTime());
            assertEquals(-50, state.quantity());
        }
    }

    // ==================== Тесты кэширования ====================

    @Nested
    @DisplayName("Кэширование")
    class CachingTests {

        @Test
        @DisplayName("Использует кэш при отсутствии новых ордеров")
        void testUsesCacheWhenNoNewOrders() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";
            Instant baseTime = Instant.parse("2025-01-01T10:00:00Z");

            Order buyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, baseTime);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(List.of(buyOrder));

            calculator.calculate(accountId, instrumentUid);

            when(orderRepository.getByAccountIdAndInstrumentUidAfterTime(eq(accountId), eq(instrumentUid), eq(baseTime)))
                .thenReturn(Collections.emptyList());

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(100, state.quantity());
            assertEquals(10.0, state.averagePrice().toDouble());
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUidAfterTime(eq(accountId), eq(instrumentUid), eq(baseTime));
        }

        @Test
        @DisplayName("Применяет только новые ордера при обновлении из кэша")
        void testAppliesOnlyNewOrdersWhenCacheHit() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant oldBuyOrderTime = Instant.parse("2025-01-01T10:00:00Z");
            Instant newBuyOrderTime = Instant.parse("2025-01-01T15:00:00Z");

            Order oldBuyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, oldBuyOrderTime);
            Order newBuyOrder = createOrder(50, 12.0, OrderDirection.BUY, ExecutionStatus.FILL, newBuyOrderTime);

            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(List.of(oldBuyOrder));

            calculator.calculate(accountId, instrumentUid);

            when(orderRepository.getByAccountIdAndInstrumentUidAfterTime(eq(accountId), eq(instrumentUid), eq(oldBuyOrderTime)))
                .thenReturn(List.of(newBuyOrder));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(150, state.quantity());
            assertEquals(10.666666666666666, state.averagePrice().toDouble(), 0.0001);
            // Время объединяется: от старой заявки до новой
            assertEquals(oldBuyOrderTime, state.timePointRange().fromTime());
            assertEquals(newBuyOrderTime, state.timePointRange().toTime());
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
            verify(orderRepository, times(1)).getByAccountIdAndInstrumentUidAfterTime(eq(accountId), eq(instrumentUid), eq(oldBuyOrderTime));
        }

        @Test
        @DisplayName("Инвалидация кэша")
        void testCacheInvalidation() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant buyOrderTime = Instant.parse("2020-09-08T17:00:00Z");
            Order buyOrder = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, buyOrderTime);
            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(List.of(buyOrder));

            EntryPrice state1 = calculator.calculate(accountId, instrumentUid);
            assertEquals(100, state1.quantity());

            BaseEntryPriceCalculator.invalidate(accountId, instrumentUid);

            Instant sellOrderTime = Instant.parse("2020-09-09T17:00:00Z");
            Order sellOrder = createOrder(50, 12.0, OrderDirection.SELL, ExecutionStatus.FILL, sellOrderTime);
            when(orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid))
                .thenReturn(Arrays.asList(buyOrder, sellOrder));

            EntryPrice state2 = calculator.calculate(accountId, instrumentUid);

            assertEquals(50, state2.quantity());
            verify(orderRepository, times(2)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
        }

        @Test
        @DisplayName("Очистка всего кэша")
        void testClearCache() {
            String accountId = "account-1";
            String instrumentUid = "instrument-1";
            Instant orderTime = Instant.parse("2021-04-07T19:14:00Z");

            Order order1 = createOrder(100, 10.0, OrderDirection.BUY, ExecutionStatus.FILL, orderTime);

            when(orderRepository.getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid)))
                .thenReturn(List.of(order1));

            EntryPrice state1 = calculator.calculate(accountId, instrumentUid);
            assertEquals(100, state1.quantity());

            BaseEntryPriceCalculator.clearCache();

            EntryPrice state2 = calculator.calculate(accountId, instrumentUid);

            verify(orderRepository, times(2)).getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid));
            assertEquals(100, state2.quantity());
        }
    }

    private Order createOrder(long lots, double price, OrderDirection direction, ExecutionStatus status, Instant time) {
        Order order = new Order();
        order.setLotsExecuted(lots);
        order.setInstrumentPrice(Quotation.of(price));
        order.setDirection(direction);
        order.setExecutionStatus(status);
        order.setInstrument(new Instrument());
        order.setExecutedTime(time);
        order.getInstrument().setUid("test-instrument");
        return order;
    }
}
