package com.siberalt.singularity.strategy.impl.quantity;

import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetPositionQuantityTest {
    private static final Instant NOW = Instant.parse("2021-06-03T10:00:00Z");

    private final TargetPositionQuantity quantity = new TargetPositionQuantity();

    @Test
    void commitsTheWholeAccountOnFullConviction() {
        assertEquals(1000, quantity.toBuy(moment(1.0), TradeCapacity.of(1000, 0)));
    }

    @Test
    void buysTheDifferenceUpToTheTarget() {
        // Eight tenths of a thousand-lot account is 800, and 300 of them are already held.
        assertEquals(500, quantity.toBuy(moment(0.8), TradeCapacity.of(700, 300)));
    }

    /**
     * The whole point of sizing to a target. The same signal on the next bar asks for nothing more,
     * where an incremental sizing would spend the leftover balance again and keep buying for as
     * long as the signal held.
     */
    @Test
    void asksForNothingOnceTheTargetIsMet() {
        assertEquals(0, quantity.toBuy(moment(0.8), TradeCapacity.of(200, 800)));
    }

    @Test
    void addsOnlyWhenTheSignalStrengthens() {
        assertEquals(0, quantity.toBuy(moment(0.8), TradeCapacity.of(200, 800)));
        assertEquals(100, quantity.toBuy(moment(0.9), TradeCapacity.of(200, 800)));
    }

    /**
     * A signal to sell wants nothing held, so the target is nothing and the position goes. Selling
     * a share of it would leave the strategy invested against its own signal.
     */
    @Test
    void closesThePositionOnAnySellSignal() {
        assertEquals(1000, quantity.toSell(moment(-1.0), TradeCapacity.of(0, 1000)));
        assertEquals(1000, quantity.toSell(moment(-0.5), TradeCapacity.of(0, 1000)));
    }

    @Test
    void sellsDownToTheTargetWhenTheSignalOnlyWeakens() {
        // Still a buy signal, just a weaker one: three tenths of the account, not eight.
        assertEquals(500, quantity.toSell(moment(0.3), TradeCapacity.of(200, 800)));
    }

    @Test
    void neverReturnsANegativeSize() {
        assertEquals(0, quantity.toBuy(moment(0.3), TradeCapacity.of(200, 800)));
        assertEquals(0, quantity.toSell(moment(1.0), TradeCapacity.of(200, 800)));
    }

    /**
     * The target falls as the price rises: the money left over buys fewer lots, so the account
     * measured in lots is smaller, and a signal that has not changed asks for nothing more.
     */
    @Test
    void doesNotChaseARisingPrice() {
        // 800 lots held, 200 more affordable - fully sized at 0.8 of a thousand.
        assertEquals(0, quantity.toBuy(moment(0.8), TradeCapacity.of(200, 800)));

        // The price doubled: the same money now buys 100. The account is 900 lots, target 720.
        assertEquals(0, quantity.toBuy(moment(0.8), TradeCapacity.of(100, 800)));
    }

    @Test
    void roundsDownToWholeLots() {
        // Seven tenths of three lots is 2.1, and there is no such thing as a tenth of a lot.
        assertEquals(2, quantity.toBuy(moment(0.7), TradeCapacity.of(3, 0)));
    }

    @Test
    void holdsOnlyTheShareOfTheAccountItIsAllowed() {
        // A tenth of a thousand-lot account at full conviction, not the whole of it.
        assertEquals(100, quantity.setFullPositionShare(0.1).toBuy(moment(1.0), TradeCapacity.of(1000, 0)));
    }

    @Test
    void scalesTheAllowedShareByConviction() {
        assertEquals(80, quantity.setFullPositionShare(0.1).toBuy(moment(0.8), TradeCapacity.of(1000, 0)));
    }

    @Test
    void sellsBackDownToTheAllowedShare() {
        // Nine hundred held where a tenth of the account is all the strategy means to hold.
        assertEquals(800, quantity.setFullPositionShare(0.1).toSell(moment(1.0), TradeCapacity.of(100, 900)));
    }

    @Test
    void refusesAShareOutsideItsRange() {
        assertThrows(IllegalArgumentException.class, () -> quantity.setFullPositionShare(0));
        assertThrows(IllegalArgumentException.class, () -> quantity.setFullPositionShare(1.5));
    }

    /**
     * Названная доля - размер сделки, а не цель: сигнал просит купить 0.4 счёта, и покупается 0.4, хотя
     * позиция уже 0.4. Потолок на инструмент её тоже не урезает - сигнал сказал, сколько торговать.
     */
    @Test
    void tradesTheShareTheSignalNamedRatherThanATarget() {
        TargetPositionQuantity capped = new TargetPositionQuantity(0.5);

        assertEquals(400, capped.toBuy(named(SignalType.POSITION_ENTRY, 1, 0.4), TradeCapacity.of(600, 400)));
        // А молчащий о размере сигнал - по-прежнему цель через потолок на инструмент.
        assertEquals(100, capped.toBuy(moment(1.0), TradeCapacity.of(600, 400)));
    }

    /** Доли - от всего счёта, а не от остатка, поэтому складываются: 0.1, 0.2 и 0.2 - половина. */
    @Test
    void addsSharesUpAgainstTheWholeAccount() {
        assertEquals(100, quantity.toSell(named(SignalType.TAKE_PROFIT, -1, 0.1), TradeCapacity.of(0, 1000)));
        assertEquals(200, quantity.toSell(named(SignalType.TAKE_PROFIT, -1, 0.2), TradeCapacity.of(100, 900)));
        assertEquals(200, quantity.toSell(named(SignalType.TAKE_PROFIT, -1, 0.2), TradeCapacity.of(300, 700)));
    }

    /** Купить можно не больше, чем по карману. */
    @Test
    void buysNoMoreThanTheMoneyAllows() {
        assertEquals(300, quantity.toBuy(named(SignalType.POSITION_ENTRY, 1, 1), TradeCapacity.of(300, 700)));
    }

    /** Выход не переворачивает: фиксация больше лонга закрывает лонг, больше шорта - шорт. */
    @Test
    void neverTurnsThePositionOnAnExit() {
        assertEquals(300, quantity.toSell(named(SignalType.TAKE_PROFIT, -1, 0.5), TradeCapacity.of(700, 300)));
        assertEquals(500, quantity.toBuy(named(SignalType.POSITION_EXIT, 1, 0.8), TradeCapacity.of(1500, -500)));
        assertEquals(250, quantity.toBuy(named(SignalType.TAKE_PROFIT, 1, 0.25), TradeCapacity.of(1500, -500)));
        assertEquals(0, quantity.toSell(named(SignalType.POSITION_EXIT, -1, 0.5), TradeCapacity.of(1000, 0)));
    }

    /**
     * Из лонга в шорт одной продажей: 800 закрывают лонг, ещё 500 - половина тысячи - открывают шорт, и
     * это сделка в 1.3 счёта. Дальше шорта на весь счёт не продаётся.
     */
    @Test
    void turnsALongIntoAShortInOneSale() {
        assertEquals(1300, quantity.toSell(named(SignalType.POSITION_ENTRY, -1, 1.3), TradeCapacity.of(200, 800)));
        assertEquals(1000, quantity.toSell(named(SignalType.POSITION_ENTRY, -1, 2), TradeCapacity.of(1000, 0)));
    }

    /**
     * Шорт - только названная доля. Продажа без неё по-прежнему значит «ничего не держать», а не
     * «встать в шорт»: иначе каждый существующий источник выхода начал бы открывать шорты.
     */
    @Test
    void neverShortsOnConfidenceAlone() {
        assertEquals(800, quantity.toSell(moment(-1.0), TradeCapacity.of(200, 800)));
        assertEquals(0, quantity.toSell(moment(-1.0), TradeCapacity.of(1000, 0)));
    }

    private TradeMoment named(SignalType type, double confidence, double share) {
        return new TradeMoment(1L, NOW, new Signal(type, confidence, 1.0, share));
    }

    private TradeMoment moment(double signal) {
        return new TradeMoment(1L, NOW, new Signal(signal, 1.0));
    }
}
