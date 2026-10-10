package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.strategy.signal.Signal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Сам контракт размера: один метод, и два написания постоянного аргумента поверх него. */
class TradeQuantityTest {
    private static final Instant NOW = Instant.parse("2021-06-03T10:00:00Z");
    private static final TradeMoment MOMENT =
        new TradeMoment(1L, NOW, new Signal(1.0, 1.0));
    private static final TradeCapacity CAPACITY = TradeCapacity.of(1000, 400);

    /** Реализуется один метод - значит контракт годится лямбдой, в том числе тест-дублем. */
    @Test
    void canBeWrittenAsALambda() {
        TradeQuantity ten = (moment, capacity, direction) -> 10;

        assertEquals(10, ten.toTrade(MOMENT, CAPACITY, OrderDirection.BUY));
        assertEquals(10, ten.toBuy(MOMENT, CAPACITY));
        assertEquals(10, ten.toSell(MOMENT, CAPACITY));
    }

    /** {@code toBuy} и {@code toSell} ничего не решают - они передают постоянное направление. */
    @Test
    void spellsTheDirectionRatherThanDecidingIt() {
        List<OrderDirection> asked = new ArrayList<>();
        TradeQuantity recording = (moment, capacity, direction) -> {
            asked.add(direction);

            return 0;
        };

        recording.toBuy(MOMENT, CAPACITY);
        recording.toSell(MOMENT, CAPACITY);

        assertEquals(List.of(OrderDirection.BUY, OrderDirection.SELL), asked);
    }

    /**
     * И у настоящих реализаций оба написания дают то же, что прямой вызов: сведение контракта к одному
     * методу поведения не поменяло.
     */
    @Test
    void keepsTheAnswersTheImplementationsGaveBefore() {
        for (TradeQuantity quantity : List.of(new SignalScaledQuantity(),
            new TargetPositionQuantity())) {
            assertEquals(quantity.toTrade(MOMENT, CAPACITY, OrderDirection.BUY),
                quantity.toBuy(MOMENT, CAPACITY), quantity.getClass().getSimpleName());
            assertEquals(quantity.toTrade(MOMENT, CAPACITY, OrderDirection.SELL),
                quantity.toSell(MOMENT, CAPACITY), quantity.getClass().getSimpleName());
        }
    }

    /**
     * Разница между сторонами у приростного размера - из чего он считает: покупка из того, что счёт может
     * купить, продажа из того, что он держит. Это и было всё содержание двух методов.
     */
    @Test
    void measuresTheBuyAgainstTheBalanceAndTheSellAgainstThePosition() {
        TradeQuantity quantity = new SignalScaledQuantity();

        assertEquals(1000, quantity.toTrade(MOMENT, CAPACITY, OrderDirection.BUY));
        assertEquals(400, quantity.toTrade(MOMENT, CAPACITY, OrderDirection.SELL));
    }
}
