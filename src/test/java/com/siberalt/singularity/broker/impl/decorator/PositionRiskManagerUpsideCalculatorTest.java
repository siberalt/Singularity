package com.siberalt.singularity.broker.impl.decorator;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandleFactory;
import com.siberalt.singularity.shared.TimePointRange;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.market.position.EntryPrice;
import com.siberalt.singularity.strategy.market.position.EntryPriceCalculator;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@DisplayName("Тесты калькулятора Upside для риск-менеджмента позиций")
public class PositionRiskManagerUpsideCalculatorTest {
    private static final String ACCOUNT_ID = "test-account";
    private static final String INSTRUMENT_UID = "TEST_INSTRUMENT";

    private EntryPriceCalculator entryPriceCalculatorMock;
    private ExtremeLocator maxLocatorMock;
    private ExtremeLocator minLocatorMock;
    private VolatilityCalculator volatilityCalculatorMock;

    @BeforeEach
    void setUp() {
        entryPriceCalculatorMock = mock(EntryPriceCalculator.class);
        maxLocatorMock = mock(ExtremeLocator.class);
        minLocatorMock = mock(ExtremeLocator.class);
        volatilityCalculatorMock = mock(VolatilityCalculator.class);
    }

    @Nested
    @DisplayName("Тесты валидации входных данных")
    class ValidationTests {
        @Test
        @DisplayName("Возвращает NEUTRAL, когда список свечей null")
        void returnsNeutralWhenCandlesListIsNull() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            Upside result = calculator.calculate(null);

            assertEquals(Upside.NEUTRAL, result);
        }

        @Test
        @DisplayName("Возвращает NEUTRAL, когда список свечей пуст")
        void returnsNeutralWhenCandlesListIsEmpty() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            List<Candle> emptyCandles = List.of();

            Upside result = calculator.calculate(emptyCandles);

            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Тесты обработки отсутствующей позиции")
    class NoPositionTests {
        @Test
        @DisplayName("Возвращает NEUTRAL, когда позиция отсутствует")
        void returnsNeutralWhenNoPositionExists() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);
            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(EntryPrice.EMPTY);

            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
            verify(entryPriceCalculatorMock).calculate(ACCOUNT_ID, INSTRUMENT_UID);
        }

        @Test
        @DisplayName("Возвращает NEUTRAL, когда баланс позиции равен нулю")
        void returnsNeutralWhenPositionBalanceIsZero() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);
            EntryPrice emptyPosition = new EntryPrice(0, null);
            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(emptyPosition);

            Upside result = calculator.calculate(candles);

            assertEquals(Upside.NEUTRAL, result);
        }
    }

    @Nested
    @DisplayName("Тесты для long-позиций")
    class LongPositionTests {
        @Test
        @DisplayName("Возвращает сигнал закрытия позиции при большом отклонении цены")
        void returnsClosePositionSignalForLargeDeviation() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 100, lastExtreme 130, averageEntryPrice 90
            // basePrice = max(90, 130) = 130
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice longPosition = new EntryPrice(10, Quotation.of(90.0), entryPriceRange);
            Candle lastExtreme = createCandle("2024-01-05T00:00:00Z", 130.0);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(longPosition);
            when(maxLocatorMock.locate(anyList())).thenReturn(List.of(lastExtreme));
            when(volatilityCalculatorMock.calculate(any())).thenReturn(0.05);

            Upside result = calculator.calculate(candles);

            assertEquals(-1, result.signal());
            assertTrue(-1 > result.strength());
        }

        @Test
        @DisplayName("Использует среднюю цену, когда последний экстремум ниже")
        void usesAveragePriceWhenLastExtremeIsBelow() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 90, lastExtreme 90, averageEntryPrice 130
            // basePrice = max(130, 90) = 130
            List<Candle> candles = createCandlesWithPrices(90.0, 90.0, 90.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice longPosition = new EntryPrice(10, Quotation.of(130.0), entryPriceRange);
            Candle lastExtreme = createCandle("2024-01-05T00:00:00Z", 90.0);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(longPosition);
            when(maxLocatorMock.locate(anyList())).thenReturn(List.of(lastExtreme));
            when(volatilityCalculatorMock.calculate(any())).thenReturn(0.05);

            Upside result = calculator.calculate(candles);

            assertEquals(-1, result.signal());
        }

        @Test
        @DisplayName("Использует цену последнего экстремума, когда он выше средней")
        void usesLastExtremePriceWhenItIsAboveAverage() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 110, lastExtreme 110, averageEntryPrice 100
            // basePrice = max(100, 110) = 110
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice longPosition = new EntryPrice(10, Quotation.of(100.0), entryPriceRange);
            Candle lastExtreme = createCandle("2024-01-05T00:00:00Z", 110.0);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(longPosition);
            when(maxLocatorMock.locate(anyList())).thenReturn(List.of(lastExtreme));
            when(volatilityCalculatorMock.calculate(any())).thenReturn(0.05);

            Upside result = calculator.calculate(candles);

            assertEquals(-1, result.signal());
        }

        @Test
        void returnsSignalOneWhenCurrentPriceIsHigherEntryPriceAndLastExtreme() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 160, lastExtreme 110, averageEntryPrice 100
            // basePrice = max(100, 110) = 110
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 160.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice longPosition = new EntryPrice(10, Quotation.of(100.0), entryPriceRange);
            Candle lastExtreme = createCandle("2024-01-05T00:00:00Z", 110.0);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(longPosition);
            when(maxLocatorMock.locate(anyList())).thenReturn(List.of(lastExtreme));
            when(volatilityCalculatorMock.calculate(any())).thenReturn(0.05);

            Upside result = calculator.calculate(candles);

            assertEquals(1, result.signal());
        }

        @Test
        @DisplayName("Возвращает NEUTRAL, когда текущая цена равна средней и нет экстремумов")
        void returnsNeutralWhenCurrentPriceEqualsEntryAndNoExtremes() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена равна средней 100, нет экстремумов
            // basePrice = averageEntryPrice = 100
            // deviation = 0, signal = 0
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice longPosition = new EntryPrice(10, Quotation.of(100.0), entryPriceRange);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(longPosition);
            when(maxLocatorMock.locate(anyList())).thenReturn(List.of());
            when(volatilityCalculatorMock.calculate(any())).thenReturn(0.05);

            Upside result = calculator.calculate(candles);

            assertEquals(0, result.signal());
        }

        @Test
        @DisplayName("Игнорирует экстремум до открытия позиции для long")
        void ignoresExtremeBeforePositionOpen() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 100, средняя 100, экстремум до открытия позиции
            // basePrice = averageEntryPrice = 100 (ignoring old extreme)
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-02T00:00:00Z"));
            EntryPrice longPosition = new EntryPrice(10, Quotation.of(100.0), entryPriceRange);
            Candle oldExtreme = createCandle("2024-01-01T00:00:00Z", 120.0);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(longPosition);
            when(maxLocatorMock.locate(anyList())).thenReturn(List.of(oldExtreme));
            when(volatilityCalculatorMock.calculate(any())).thenReturn(0.05);

            Upside result = calculator.calculate(candles);

            assertEquals(0, result.signal());
        }
    }

    @Nested
    @DisplayName("Тесты для short-позиций")
    class ShortPositionTests {
        @Test
        @DisplayName("Возвращает сигнал закрытия позиции при большом отклонении цены для short")
        void returnsClosePositionSignalForLargeDeviation() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 110, lastExtreme 100 (из candles.get(2)), averageEntryPrice 100
            // basePrice = min(100, 100) = 100
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 110.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice shortPosition = new EntryPrice(-10, Quotation.of(100.0), entryPriceRange);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(shortPosition);
            when(minLocatorMock.locate(anyList())).thenReturn(List.of(candles.get(2)));
            when(volatilityCalculatorMock.calculate(anyList())).thenReturn(0.1);

            Upside result = calculator.calculate(candles);

            assertEquals(1, result.signal());
            assertTrue(1 <= result.strength());
        }

        @Test
        @DisplayName("Использует среднюю цену, когда последний экстремум выше для short")
        void usesAveragePriceWhenLastExtremeIsAbove() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 100, lastExtreme 110, averageEntryPrice 90
            // basePrice = min(90, 110) = 90
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice shortPosition = new EntryPrice(-10, Quotation.of(90.0), entryPriceRange);
            Candle lastExtreme = createCandle("2024-01-05T00:00:00Z", 110.0);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(shortPosition);
            when(minLocatorMock.locate(anyList())).thenReturn(List.of(lastExtreme));
            when(volatilityCalculatorMock.calculate(anyList())).thenReturn(0.1);

            Upside result = calculator.calculate(candles);

            assertEquals(1, result.signal());
        }

        @Test
        @DisplayName("Использует цену последнего экстремума, когда он ниже средней для short")
        void usesLastExtremePriceWhenItIsBelowAverage() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 100, lastExtreme 90, averageEntryPrice 120
            // basePrice = min(120, 90) = 90
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice shortPosition = new EntryPrice(-10, Quotation.of(120.0), entryPriceRange);
            Candle lastExtreme = createCandle("2024-01-05T00:00:00Z", 90.0);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(shortPosition);
            when(minLocatorMock.locate(anyList())).thenReturn(List.of(lastExtreme));

            Upside result = calculator.calculate(candles);

            assertEquals(1, result.signal());
        }

        @Test
        @DisplayName("Возвращает сигнал -1, когда текущая цена ниже последнего экстремума и цены входа для short")
        void returnsSignalMinusOneWhenCurrentPriceIsLessLastExtremeAndEntryPrice() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 70, lastExtreme 90, averageEntryPrice 80
            // basePrice = min(80, 90) = 80
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 70.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice shortPosition = new EntryPrice(-10, Quotation.of(80.0), entryPriceRange);
            Candle lastExtreme = createCandle("2024-01-05T00:00:00Z", 90.0);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(shortPosition);
            when(minLocatorMock.locate(anyList())).thenReturn(List.of(lastExtreme));

            Upside result = calculator.calculate(candles);

            assertEquals(-1, result.signal());
        }

        @Test
        @DisplayName("Возвращает NEUTRAL, когда текущая цена равна средней и нет экстремумов для short")
        void returnsNeutralWhenCurrentPriceEqualsEntryAndNoExtremes() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена равна средней 100, нет экстремумов
            // basePrice = averageEntryPrice = 100
            // deviation = 0, signal = 0
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-01T00:00:00Z"));
            EntryPrice shortPosition = new EntryPrice(-10, Quotation.of(100.0), entryPriceRange);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(shortPosition);
            when(minLocatorMock.locate(anyList())).thenReturn(List.of());
            when(volatilityCalculatorMock.calculate(anyList())).thenReturn(0.1);

            Upside result = calculator.calculate(candles);

            assertEquals(0, result.signal());
        }

        @Test
        @DisplayName("Игнорирует экстремум до открытия позиции для short")
        void ignoresExtremeBeforePositionOpen() {
            PositionRiskManagerUpsideCalculator calculator = createCalculator();
            // Текущая цена 100, средняя 100, экстремум до открытия позиции
            // basePrice = averageEntryPrice = 100 (ignoring old extreme)
            List<Candle> candles = createCandlesWithPrices(100.0, 100.0, 100.0);

            TimePointRange entryPriceRange = new TimePointRange(Instant.parse("2024-01-02T00:00:00Z"));
            EntryPrice shortPosition = new EntryPrice(-10, Quotation.of(100.0), entryPriceRange);
            Candle oldExtreme = createCandle("2024-01-01T00:00:00Z", 80.0);

            when(entryPriceCalculatorMock.calculate(any(), any())).thenReturn(shortPosition);
            when(minLocatorMock.locate(anyList())).thenReturn(List.of(oldExtreme));
            when(volatilityCalculatorMock.calculate(anyList())).thenReturn(0.1);

            Upside result = calculator.calculate(candles);

            assertEquals(0, result.signal());
        }
    }

    // ==================== Вспомогательные методы ====================
    
    private PositionRiskManagerUpsideCalculator createCalculator() {
        return new PositionRiskManagerUpsideCalculator(
            ACCOUNT_ID,
            entryPriceCalculatorMock,
            volatilityCalculatorMock,
            maxLocatorMock,
            minLocatorMock,
            Candle::close
        );
    }

    private Candle createCandle(String time, double closePrice) {
        CandleFactory factory = new CandleFactory(INSTRUMENT_UID);
        return factory.createCommon(time, closePrice);
    }

    private List<Candle> createCandlesWithPrices(double... closePrices) {
        CandleFactory factory = new CandleFactory(INSTRUMENT_UID);
        List<Candle> candles = new java.util.ArrayList<>();
        for (int i = 0; i < closePrices.length; i++) {
            String time = "2024-01-01T00:00:" + String.format("%02d", i) + "Z";
            candles.add(factory.createCommon(time, closePrices[i]));
        }
        return candles;
    }
}

