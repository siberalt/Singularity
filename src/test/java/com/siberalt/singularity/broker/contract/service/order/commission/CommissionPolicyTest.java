package com.siberalt.singularity.broker.contract.service.order.commission;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Тарифы: доля оборота, доли по сторонам и минимум за исполнение. */
class CommissionPolicyTest {
    private static final double CENT = 0.005;

    @Nested
    class Flat {
        @Test
        void chargesAShareOfTheNotionalAsACost() {
            // 10 лотов по 5 бумаг по 200 рублей - оборот 10 000, полпроцента с него - 50.
            assertEquals(-50, charged(flat(CENT), order(200, 10, 5, OrderDirection.BUY), 10), 1e-9);
        }

        @Test
        void chargesTheLotsFilledRatherThanTheLotsAsked() {
            Order order = order(200, 10, 5, OrderDirection.BUY);

            assertEquals(-15, charged(flat(CENT), order, 3), 1e-9);
        }

        @Test
        void chargesASaleTheSameAsAPurchase() {
            assertEquals(flat(CENT).of(order(200, 10, 5, OrderDirection.BUY), 10)
                    .getQuotation().toDouble(),
                flat(CENT).of(order(200, 10, 5, OrderDirection.SELL), 10)
                    .getQuotation().toDouble(), 1e-9);
        }

        @Test
        void refusesAShareOutsideZeroToOne() {
            assertThrows(IllegalArgumentException.class, () -> new FlatRateCommission(-0.01));
            assertThrows(IllegalArgumentException.class, () -> new FlatRateCommission(1.5));
        }
    }

    @Nested
    class PerSide {
        @Test
        void chargesEachSideItsOwnShare() {
            SideCommission tariff = new SideCommission(0.0004, 0.0006, 0);

            assertEquals(-4, tariff.of(order(200, 10, 5, OrderDirection.BUY), 10)
                .getQuotation().toDouble(), 1e-9);
            assertEquals(-6, tariff.of(order(200, 10, 5, OrderDirection.SELL), 10)
                .getQuotation().toDouble(), 1e-9);
        }

        /** Чем и отличается настоящий тариф: мелкий ордер платит минимум, а не долю. */
        @Test
        void takesTheFloorWhenTheShareIsSmaller() {
            SideCommission tariff = new SideCommission(0.0005, 40);

            assertEquals(-40, tariff.of(order(200, 1, 1, OrderDirection.BUY), 1)
                .getQuotation().toDouble(), 1e-9);
            // Оборот 200 000, доля 100 - минимум уже не связывает.
            assertEquals(-100, tariff.of(order(200, 200, 5, OrderDirection.BUY), 200)
                .getQuotation().toDouble(), 1e-9);
        }

        @Test
        void refusesANegativeFloor() {
            assertThrows(IllegalArgumentException.class, () -> new SideCommission(0.0005, -1));
        }
    }

    @Test
    void chargesNothingWhenFree() {
        assertEquals(0, CommissionPolicy.FREE.of(order(200, 10, 5, OrderDirection.BUY), 10)
            .getQuotation().toDouble(), 1e-9);
    }

    private static double charged(CommissionPolicy policy, Order order, long lots) {
        return policy.of(order, lots).getQuotation().toDouble();
    }

    private static FlatRateCommission flat(double rate) {
        return new FlatRateCommission(rate);
    }

    private static Order order(double price, long lots, int lotSize, OrderDirection direction) {
        return new Order()
            .setInstrument(new Instrument().setLot(lotSize).setCurrency("RUB"))
            .setInstrumentPrice(Quotation.of(price))
            .setLotsRequested(lots)
            .setDirection(direction);
    }
}
