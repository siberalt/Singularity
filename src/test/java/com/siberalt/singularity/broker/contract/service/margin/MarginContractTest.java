package com.siberalt.singularity.broker.contract.service.margin;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.entity.instrument.Instrument;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Контракт маржи: ставки риска и цена занятых денег. */
class MarginContractTest {
    private static final String UID = "e6123145-9665-43e0-8413-cd61b8aa9b13";
    private static final Instant DAY = Instant.parse("2024-03-01T10:00:00Z");

    @Nested
    class Requirements {
        @Test
        void keepsLongAndShortApartAndInitialApartFromMaintenance() {
            MarginRequirement rates = new MarginRequirement(0.2, 0.3, 0.15, 0.25);

            assertEquals(0.2, rates.initial(true));
            assertEquals(0.3, rates.initial(false));
            assertEquals(0.15, rates.maintenance(true));
            assertEquals(0.25, rates.maintenance(false));
        }

        /** Поддерживающая ставка строже начальной - это не «строгий брокер», а перевёрнутые поля. */
        @Test
        void refusesMaintenanceStricterThanInitial() {
            assertThrows(IllegalArgumentException.class,
                () -> new MarginRequirement(0.2, 0.3, 0.25, 0.25));
            assertThrows(IllegalArgumentException.class,
                () -> new MarginRequirement(0.2, 0.3, 0.15, 0.4));
        }

        @Test
        void refusesANonPositiveRateButAllowsMoreThanFullCover() {
            assertThrows(IllegalArgumentException.class, () -> MarginRequirement.of(0));
            assertThrows(IllegalArgumentException.class, () -> MarginRequirement.of(-0.1));
            // Шорт дороже своей стоимости - у брокера так и есть на тонких бумагах.
            assertEquals(1.1, new MarginRequirement(0.3, 1.1, 0.25, 0.9999).initial(false));
        }

        @Test
        void coversEverythingInACashAccount() {
            assertEquals(1, MarginRequirement.CASH.initial(true));
            assertEquals(1, MarginRequirement.CASH.maintenance(false));
        }
    }

    @Nested
    class Table {
        @Test
        void findsRatesByUid() {
            MarginRequirement mine = new MarginRequirement(0.2, 0.3, 0.15, 0.25);
            TabulatedMarginPolicy policy = new TabulatedMarginPolicy(Map.of(UID, mine));

            assertSame(mine, policy.of(new Instrument().setUid(UID)));
        }

        /** Неизвестная бумага - не средний риск, а незнание: полное покрытие, то есть без плеча. */
        @Test
        void demandsFullCoverForAnInstrumentItDoesNotKnow() {
            TabulatedMarginPolicy policy = new TabulatedMarginPolicy(
                Map.of(UID, MarginRequirement.of(0.2)));

            assertEquals(MarginRequirement.CASH, policy.of(new Instrument().setUid("другой")));
            assertEquals(MarginRequirement.CASH, policy.of(new Instrument()));
            assertEquals(MarginRequirement.CASH, policy.of(null));
        }

        @Test
        void takesTheFallbackItWasGiven() {
            MarginRequirement loose = MarginRequirement.of(0.5);

            assertEquals(loose, new TabulatedMarginPolicy(Map.of(), loose).of(new Instrument().setUid(UID)));
        }
    }

    @Nested
    class Funding {
        @Test
        void chargesAFixedRateByTheDay() {
            // 100 000 под 20% годовых за 73 дня - пятая часть года, то есть 4000.
            Money charge = new FixedRateFunding(0.2)
                .over(Money.of("RUB", 100000.0), DAY, DAY.plus(Duration.ofDays(73)));

            assertEquals(-4000, charge.getQuotation().toDouble(), 1e-6);
        }

        /** Нулевой отрезок - это не ошибка: позиция, открытая и закрытая в один миг, платы не стоит. */
        @Test
        void chargesNothingForNoTime() {
            assertEquals(0, new FixedRateFunding(0.2)
                .over(Money.of("RUB", 100000.0), DAY, DAY).getQuotation().toDouble(), 1e-9);
            assertEquals(0, new CurveFunding(RateCurve.flat(), 0.2)
                .over(Money.of("RUB", 100000.0), DAY, DAY).getQuotation().toDouble(), 1e-9);
            assertEquals(0, FundingPolicy.FREE
                .over(Money.of("RUB", 100000.0), DAY, DAY.plusSeconds(1)).getQuotation().toDouble(), 1e-9);
        }

        /**
         * А вот отсутствующая сумма, отсутствующая дата и заём, кончившийся раньше начала, - ошибки
         * вызывающего. Сначала они молча давали ноль, то есть сломанное начисление выглядело как бесплатное.
         */
        @Test
        void refusesALoanThatMakesNoSense() {
            for (FundingPolicy policy : java.util.List.of(new FixedRateFunding(0.2),
                new CurveFunding(RateCurve.flat(), 0.04), FundingPolicy.FREE)) {
                assertThrows(IllegalArgumentException.class,
                    () -> policy.over(null, DAY, DAY.plusSeconds(1)));
                assertThrows(IllegalArgumentException.class,
                    () -> policy.over(Money.of("RUB", 1000.0), null, DAY));
                assertThrows(IllegalArgumentException.class,
                    () -> policy.over(Money.of("RUB", 1000.0), DAY, null));
                assertThrows(IllegalArgumentException.class,
                    () -> policy.over(Money.of("RUB", 1000.0), DAY, DAY.minusSeconds(1)));
            }
        }

        /** Кривая: плата - это то, на сколько вырос фонд за ровно этот отрезок, плюс спред. */
        @Test
        void chargesWhatTheCurveGrewPlusTheSpread() {
            NavigableMap<Long, Double> prices = new TreeMap<>();

            prices.put(DAY.toEpochMilli(), 100.0);
            prices.put(DAY.plus(Duration.ofDays(365)).toEpochMilli(), 116.0);

            Money charge = new CurveFunding(RateCurve.ofPrices(prices), 0.04)
                .over(Money.of("RUB", 1000.0), DAY, DAY.plus(Duration.ofDays(365)));

            // 16% от кривой плюс 4% спреда за год - ровно 200 рублей с тысячи.
            assertEquals(-200, charge.getQuotation().toDouble(), 1e-6);
        }

        @Test
        void takesAnyFunctionOfTimeAsACurve() {
            // Кривая не обязана быть таблицей: ровно 10% за год, заданные формулой.
            RateCurve tenPercent = moment -> Math.pow(1.1,
                (double) Duration.between(DAY, moment).toMillis() / Duration.ofDays(365).toMillis());

            assertEquals(-100, new CurveFunding(tenPercent, 0)
                .over(Money.of("RUB", 1000.0), DAY, DAY.plus(Duration.ofDays(365)))
                .getQuotation().toDouble(), 1e-6);
        }

        @Test
        void carriesTheLastPriceForwardOutsideTheCurve() {
            NavigableMap<Long, Double> prices = new TreeMap<>();

            prices.put(DAY.toEpochMilli(), 100.0);

            Money charge = new CurveFunding(RateCurve.ofPrices(prices), 0.0365)
                .over(Money.of("RUB", 1000.0), DAY, DAY.plus(Duration.ofDays(10)));

            // Роста нет - остаётся один спред: 3.65% годовых за десять дней с тысячи.
            assertEquals(-1, charge.getQuotation().toDouble(), 1e-6);
        }

        @Test
        void standsAtTheFirstPriceBeforeTheCurveStarts() {
            NavigableMap<Long, Double> prices = new TreeMap<>();

            prices.put(DAY.toEpochMilli(), 100.0);

            assertEquals(100, RateCurve.ofPrices(prices).at(DAY.minus(Duration.ofDays(5))), 1e-9);
        }

        @Test
        void refusesANegativeRateOrSpreadAndAnEmptyCurve() {
            assertThrows(IllegalArgumentException.class, () -> new FixedRateFunding(-0.01));
            assertThrows(IllegalArgumentException.class, () -> new CurveFunding(RateCurve.flat(), -0.01));
            assertThrows(IllegalArgumentException.class, () -> new CurveFunding(null, 0.01));
            assertThrows(IllegalArgumentException.class, () -> RateCurve.ofPrices(new TreeMap<>()));
            assertThrows(IllegalArgumentException.class, () -> RateCurve.ofPrices(null));
        }

        @Test
        void chargesMoreTheLongerAShortStands() {
            FundingPolicy funding = new FixedRateFunding(0.2);
            double aDay = funding.over(Money.of("RUB", 1000.0), DAY, DAY.plus(Duration.ofDays(1)))
                .getQuotation().toDouble();
            double aMonth = funding.over(Money.of("RUB", 1000.0), DAY, DAY.plus(Duration.ofDays(30)))
                .getQuotation().toDouble();

            assertTrue(aMonth < aDay, "месяц должен стоить дороже дня: " + aMonth + " против " + aDay);
            assertEquals(30 * aDay, aMonth, 1e-6);
        }
    }
}
