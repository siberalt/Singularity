package com.siberalt.singularity.strategy.market.position;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.operation.OperationType;
import com.siberalt.singularity.entity.operation.ReadOperationRepository;
import com.siberalt.singularity.shared.TimePointRange;
import com.siberalt.singularity.shared.TimeRange;
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
    private ReadOperationRepository operationRepository;
    private BaseEntryPriceCalculator calculator;

    @BeforeEach
    void setUp() {
        operationRepository = mock(ReadOperationRepository.class);
        calculator = new BaseEntryPriceCalculator(operationRepository);
    }

    @AfterEach
    void tearDown() {
        BaseEntryPriceCalculator.clearCache();
    }

    /** Mirrors BaseEntryPriceCalculator's own "since cache checkpoint" range construction. */
    private static TimeRange sinceCache(Instant checkpoint) {
        return new TimeRange(checkpoint.plusNanos(1), Instant.MAX);
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
            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Collections.emptyList());

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertTrue(state.isEmpty());
            assertEquals(EntryPrice.EMPTY, state);
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
        }

        @Test
        @DisplayName("Агрегирует ордера при первом вызове (кэш пуст)")
        void testAggregateFromOrdersOnFirstCall() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Operation buyOperation1 = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation buyOperation2 = createOperation(50, 12.0, OperationType.BUY, OperationState.EXECUTED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation1, buyOperation2));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(150, state.quantity());
            assertEquals(10.666666666666666, state.averagePrice().toDouble(), 0.0001);
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
        }

        @Test
        @DisplayName("Игнорирует неисполненные ордера при агрегации")
        void testIgnoresUnfilledOrders() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Operation filledOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation unfilledOperation = createOperation(50, 15.0, OperationType.BUY, OperationState.CANCELED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(filledOperation, unfilledOperation));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(100, state.quantity());
            assertEquals(10.0, state.averagePrice().toDouble());
            assertEquals(new TimePointRange(new TimePoint(time1)), state.timePointRange());
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
        }

        @Test
        @DisplayName("Тест с несколькими покупками подряд")
        void testMultipleBuysInSequence() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");
            Instant time3 = Instant.parse("2024-10-30T18:00:00Z");

            Operation buyOperation1 = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation buyOperation2 = createOperation(100, 12.0, OperationType.BUY, OperationState.EXECUTED, time2);
            Operation buyOperation3 = createOperation(100, 14.0, OperationType.BUY, OperationState.EXECUTED, time3);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation1, buyOperation2, buyOperation3));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            TimePointRange expectedRange = new TimePointRange(
                new TimePoint(time1),
                new TimePoint(time3)
            );
            assertEquals(300, state.quantity());
            assertEquals(12.0, state.averagePrice().toDouble());
            assertEquals(expectedRange, state.timePointRange());
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
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

            Operation buyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation sellOperation = createOperation(50, 12.0, OperationType.SELL, OperationState.EXECUTED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation, sellOperation));

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

            Operation buyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation sellOperation = createOperation(150, 12.0, OperationType.SELL, OperationState.EXECUTED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation, sellOperation));

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

            Operation buyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation sellOperation = createOperation(100, 10.0, OperationType.SELL, OperationState.EXECUTED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation, sellOperation));

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

            Operation sellOperation1 = createOperation(100, 10.0, OperationType.SELL, OperationState.EXECUTED, time1);
            Operation sellOperation2 = createOperation(100, 12.0, OperationType.SELL, OperationState.EXECUTED, time2);
            Operation sellOperation3 = createOperation(100, 14.0, OperationType.SELL, OperationState.EXECUTED, time3);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(sellOperation1, sellOperation2, sellOperation3));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            TimePointRange expectedRange = new TimePointRange(
                new TimePoint(time1),
                new TimePoint(time3)
            );
            assertEquals(-300, state.quantity());
            assertEquals(12.0, state.averagePrice().toDouble());
            assertEquals(expectedRange, state.timePointRange());
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
        }

        @Test
        @DisplayName("Тест частичного закрытия позиции")
        void testPartialPositionClosure() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Operation buyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation sellOperation = createOperation(60, 12.0, OperationType.SELL, OperationState.EXECUTED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation, sellOperation));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(40, state.quantity());
            assertEquals(10.0, state.averagePrice().toDouble());
            assertEquals(new TimePointRange(new TimePoint(time1)), state.timePointRange());
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
        }

        @Test
        @DisplayName("Тест с нулевым количеством (отмена)")
        void testZeroQuantityWhenEqualBuyAndSell() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant time1 = Instant.parse("2024-09-28T18:00:00Z");
            Instant time2 = Instant.parse("2024-09-29T18:00:00Z");

            Operation buyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation sellOperation = createOperation(100, 15.0, OperationType.SELL, OperationState.EXECUTED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation, sellOperation));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertTrue(state.isEmpty());
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
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
            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
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

            Operation buyOperation1 = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation buyOperation2 = createOperation(50, 12.0, OperationType.BUY, OperationState.EXECUTED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation1, buyOperation2));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(time1, state.timePointRange().fromTime());
            assertEquals(time2, state.timePointRange().toTime());
        }

        @Test
        @DisplayName("TimePointRange объединяется при обновлении из кэша")
        void testTimePointRangeUnionWithCache() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant oldBuyOperationTime = Instant.parse("2025-01-01T10:00:00Z");
            Instant newBuyOperationTime = Instant.parse("2025-01-01T15:00:00Z");

            Operation oldBuyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, oldBuyOperationTime);
            Operation newBuyOperation = createOperation(50, 12.0, OperationType.BUY, OperationState.EXECUTED, newBuyOperationTime);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(List.of(oldBuyOperation));

            EntryPrice entryPrice1 = calculator.calculate(accountId, instrumentUid);
            assertEquals(oldBuyOperationTime, entryPrice1.timePointRange().fromTime());
            assertEquals(oldBuyOperationTime, entryPrice1.timePointRange().toTime());

            when(operationRepository.getByAccountIdAndInstrumentUid(
                eq(accountId), eq(instrumentUid), eq(sinceCache(oldBuyOperationTime))
            )).thenReturn(List.of(newBuyOperation));

            EntryPrice entryPrice = calculator.calculate(accountId, instrumentUid);

            // Время объединяется: от старой заявки до новой
            assertEquals(oldBuyOperationTime, entryPrice.timePointRange().fromTime());
            assertEquals(newBuyOperationTime, entryPrice.timePointRange().toTime());
        }

        @Test
        @DisplayName("TimePointRange обновляется при последовательных покупках")
        void testTimePointRangeWithSequentialBuys() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";
            Instant time1 = Instant.parse("2025-01-01T09:00:00Z");
            Instant time2 = Instant.parse("2025-01-01T10:00:00Z");
            Instant time3 = Instant.parse("2025-01-01T11:00:00Z");

            Operation buyOperation1 = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation buyOperation2 = createOperation(100, 12.0, OperationType.BUY, OperationState.EXECUTED, time2);
            Operation buyOperation3 = createOperation(100, 14.0, OperationType.BUY, OperationState.EXECUTED, time3);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation1, buyOperation2, buyOperation3));

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

            Operation buyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation sellOperation = createOperation(50, 12.0, OperationType.SELL, OperationState.EXECUTED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation, sellOperation));

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

            Operation buyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, time1);
            Operation sellOperation = createOperation(150, 12.0, OperationType.SELL, OperationState.EXECUTED, time2);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation, sellOperation));

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

            Operation buyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, baseTime);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(List.of(buyOperation));

            calculator.calculate(accountId, instrumentUid);

            when(operationRepository.getByAccountIdAndInstrumentUid(
                eq(accountId), eq(instrumentUid), eq(sinceCache(baseTime))
            )).thenReturn(Collections.emptyList());

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(100, state.quantity());
            assertEquals(10.0, state.averagePrice().toDouble());
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(sinceCache(baseTime)));
        }

        @Test
        @DisplayName("Применяет только новые ордера при обновлении из кэша")
        void testAppliesOnlyNewOrdersWhenCacheHit() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant oldBuyOperationTime = Instant.parse("2025-01-01T10:00:00Z");
            Instant newBuyOperationTime = Instant.parse("2025-01-01T15:00:00Z");

            Operation oldBuyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, oldBuyOperationTime);
            Operation newBuyOperation = createOperation(50, 12.0, OperationType.BUY, OperationState.EXECUTED, newBuyOperationTime);

            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(List.of(oldBuyOperation));

            calculator.calculate(accountId, instrumentUid);

            when(operationRepository.getByAccountIdAndInstrumentUid(
                eq(accountId), eq(instrumentUid), eq(sinceCache(oldBuyOperationTime))
            )).thenReturn(List.of(newBuyOperation));

            EntryPrice state = calculator.calculate(accountId, instrumentUid);

            assertEquals(150, state.quantity());
            assertEquals(10.666666666666666, state.averagePrice().toDouble(), 0.0001);
            // Время объединяется: от старой заявки до новой
            assertEquals(oldBuyOperationTime, state.timePointRange().fromTime());
            assertEquals(newBuyOperationTime, state.timePointRange().toTime());
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
            verify(operationRepository, times(1))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(sinceCache(oldBuyOperationTime)));
        }

        @Test
        @DisplayName("Инвалидация кэша")
        void testCacheInvalidation() {
            String accountId = "test-account";
            String instrumentUid = "test-instrument";

            Instant buyOperationTime = Instant.parse("2020-09-08T17:00:00Z");
            Operation buyOperation = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, buyOperationTime);
            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(List.of(buyOperation));

            EntryPrice state1 = calculator.calculate(accountId, instrumentUid);
            assertEquals(100, state1.quantity());

            BaseEntryPriceCalculator.invalidate(accountId, instrumentUid);

            Instant sellOperationTime = Instant.parse("2020-09-09T17:00:00Z");
            Operation sellOperation = createOperation(50, 12.0, OperationType.SELL, OperationState.EXECUTED, sellOperationTime);
            when(operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX))
                .thenReturn(Arrays.asList(buyOperation, sellOperation));

            EntryPrice state2 = calculator.calculate(accountId, instrumentUid);

            assertEquals(50, state2.quantity());
            verify(operationRepository, times(2))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
        }

        @Test
        @DisplayName("Очистка всего кэша")
        void testClearCache() {
            String accountId = "account-1";
            String instrumentUid = "instrument-1";
            Instant operationTime = Instant.parse("2021-04-07T19:14:00Z");

            Operation operation1 = createOperation(100, 10.0, OperationType.BUY, OperationState.EXECUTED, operationTime);

            when(operationRepository.getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX)))
                .thenReturn(List.of(operation1));

            EntryPrice state1 = calculator.calculate(accountId, instrumentUid);
            assertEquals(100, state1.quantity());

            BaseEntryPriceCalculator.clearCache();

            EntryPrice state2 = calculator.calculate(accountId, instrumentUid);

            verify(operationRepository, times(2))
                .getByAccountIdAndInstrumentUid(eq(accountId), eq(instrumentUid), eq(TimeRange.MAX));
            assertEquals(100, state2.quantity());
        }
    }

    private Operation createOperation(
        long quantityDone,
        double price,
        OperationType direction,
        OperationState state,
        Instant executedDate
    ) {
        return Operation.builder()
            .instrumentUid("test-instrument")
            .direction(direction)
            .quantityDone(quantityDone)
            .price(Quotation.of(price))
            .state(state)
            .executedDate(executedDate)
            .build();
    }
}
