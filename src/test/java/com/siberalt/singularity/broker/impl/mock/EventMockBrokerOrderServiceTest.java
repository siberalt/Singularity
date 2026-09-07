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
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.CandlePriceField;
import com.siberalt.singularity.entity.candle.ComparisonOperator;
import com.siberalt.singularity.entity.candle.FindPriceParams;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
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
        when(candleStorage.findByPrice(any())).thenReturn(List.of(buySignalCandle));

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

        // findByPrice finds no candle crossing the limit, so the order is scheduled to expire
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
        when(candleStorage.findByPrice(any())).thenReturn(List.of(sellSignalCandle));

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

    /**
     * A parked buy is waiting for the price to come down to it, so what it looks for is a candle
     * whose low reaches the limit. Searching the open price instead answers a different question -
     * where the price stood at one instant - and would both miss limits crossed inside a bar and,
     * with the comparison the other way round, match a candle sitting on the wrong side of the
     * limit entirely.
     */
    @Test
    public void testBuyLimitLooksForTheMarketFallingToItsPrice() throws AbstractException {
        Candle marketCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addMoney(marketCandle.open().multiply(1000));
        when(candleStorage.findByPrice(any())).thenReturn(List.of());
        stubLastCandleOfLifeTime(marketCandle);

        assertBuyParked(marketCandle, 10, Quotation.of(9));

        verify(candleStorage).findByPrice(
            priceSearch(Quotation.of(9), CandlePriceField.LOW, ComparisonOperator.LESS_OR_EQUAL)
        );
    }

    @Test
    public void testSellLimitLooksForTheMarketRisingToItsPrice() throws AbstractException {
        Candle marketCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addInstruments(80);
        when(candleStorage.findByPrice(any())).thenReturn(List.of());
        stubLastCandleOfLifeTime(marketCandle);

        assertSellParked(marketCandle, 10, Quotation.of(11));

        verify(candleStorage).findByPrice(
            priceSearch(Quotation.of(11), CandlePriceField.HIGH, ComparisonOperator.MORE_OR_EQUAL)
        );
    }

    @Test
    public void testBuyLimitFillsAtItsOwnPriceWhenTheMarketOnlyDipsToIt() throws AbstractException {
        Candle marketCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        // Opens at 12, well above the limit, and reaches 9 only inside the bar.
        Candle triggerCandle = createCandle(
            triggerTime(), 12, 13, 9, 12, 100
        );

        addMoney(marketCandle.open().multiply(1000));
        when(candleStorage.findByPrice(any())).thenReturn(List.of(triggerCandle));

        PostOrderResponse parked = assertBuyParked(marketCandle, 10, Quotation.of(9));

        assertFillsAt(parked, Quotation.of(9), OrderDirection.BUY);
    }

    /**
     * The one case where a limit order does better than its own price: the market gapped straight
     * past the limit, so there was nothing to buy at 9 and the fill happens at the 8 the candle
     * opened at.
     */
    @Test
    public void testBuyLimitFillsAtTheOpenWhenTheMarketGapsPastItsPrice() throws AbstractException {
        Candle marketCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        Candle triggerCandle = createCandle(
            triggerTime(), 8, 8, 7, 8, 100
        );

        addMoney(marketCandle.open().multiply(1000));
        when(candleStorage.findByPrice(any())).thenReturn(List.of(triggerCandle));

        PostOrderResponse parked = assertBuyParked(marketCandle, 10, Quotation.of(9));

        assertFillsAt(parked, Quotation.of(8), OrderDirection.BUY);
    }

    @Test
    public void testSellLimitFillsAtItsOwnPriceWhenTheMarketOnlyReachesIt() throws AbstractException {
        Candle marketCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        // Opens at 10, below the limit, and touches 11 only inside the bar.
        Candle triggerCandle = createCandle(
            triggerTime(), 10, 11, 9, 10, 100
        );

        addInstruments(80);
        when(candleStorage.findByPrice(any())).thenReturn(List.of(triggerCandle));

        PostOrderResponse parked = assertSellParked(marketCandle, 10, Quotation.of(11));

        assertFillsAt(parked, Quotation.of(11), OrderDirection.SELL);
    }

    @Test
    public void testSellLimitFillsAtTheOpenWhenTheMarketGapsPastItsPrice() throws AbstractException {
        Candle marketCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );
        Candle triggerCandle = createCandle(
            triggerTime(), 13, 14, 13, 13, 100
        );

        addInstruments(80);
        when(candleStorage.findByPrice(any())).thenReturn(List.of(triggerCandle));

        PostOrderResponse parked = assertSellParked(marketCandle, 10, Quotation.of(11));

        assertFillsAt(parked, Quotation.of(13), OrderDirection.SELL);
    }

    /**
     * The point of a working remainder: an order too large for one bar is not cut down to what that
     * bar could give, it keeps going until it has all of it. Three bars of a hundred lots at a tenth
     * each is exactly thirty, so the order finishes on the third.
     */
    @Test
    public void testOrderTooLargeForOneBarFillsAcrossSeveralOfThem() throws AbstractException {
        Candle firstBar = createCandle(currentTime, 10, 15, 5, 10, 100);
        Candle secondBar = createCandle(currentTime.plus(Duration.ofMinutes(1)), 10, 15, 5, 10, 100);
        Candle thirdBar = createCandle(currentTime.plus(Duration.ofMinutes(2)), 10, 15, 5, 10, 100);

        orderService.getLiquidityModel().setInfiniteLiquidity(false).setParticipationRate(0.1);
        addMoney(Quotation.of(100000));
        when(candleStorage.findAfterOrEqual(eq(config.getInstrument().getUid()), any(), eq(1L)))
            .thenReturn(List.of(secondBar))
            .thenReturn(List.of(thirdBar));

        PostOrderResponse posted = postBuy(firstBar, OrderType.MARKET, 30, null);

        assertEquals(10, posted.getLotsExecuted());
        assertEquals(ExecutionStatus.PARTIALLYFILL, posted.getExecutionStatus());

        tickAt(secondBar.getTime());

        assertEquals(20, stateOf(posted).getLotsExecuted());
        assertEquals(ExecutionStatus.PARTIALLYFILL, stateOf(posted).getExecutionStatus());
        assertEquals(20, freePositionLots());

        tickAt(thirdBar.getTime());

        assertEquals(30, stateOf(posted).getLotsExecuted());
        assertEquals(ExecutionStatus.FILL, stateOf(posted).getExecutionStatus());
        assertEquals(30, freePositionLots());

        // One order, but three separate trades - each bar it actually traded against.
        List<Operation> trades = operationRepository.getByAccountId(testAccount.getId(), TimeRange.MAX)
            .stream()
            .filter(operation -> operation.price() != null)
            .toList();

        assertEquals(3, trades.size());
        assertTrue(trades.stream().allMatch(operation -> operation.quantityDone() == 10));

        // Paid for thirty lots in the end, no more and no less, commission included.
        Quotation expectedSpend = firstBar.open()
            .multiply(30)
            .add(expectedCommissionCost(firstBar.open(), 30));

        assertEquals(
            Money.of(config.getInstrument().getCurrency(), Quotation.of(100000).subtract(expectedSpend)),
            broker.getOperationsService()
                .getAvailableMoney(testAccount.getId(), config.getInstrument().getCurrency())
        );
    }

    /**
     * A remainder that runs out of market stops being a remainder. The order keeps what it filled
     * and is done - it is not a rejection, because it traded.
     */
    @Test
    public void testRemainderStopsWhenTheMarketRunsOut() throws AbstractException {
        Candle onlyBar = createCandle(currentTime, 10, 15, 5, 10, 100);

        orderService.getLiquidityModel().setInfiniteLiquidity(false).setParticipationRate(0.1);
        addMoney(Quotation.of(100000));
        // Nothing further to trade against: no next bar, and the data ends where it started.
        when(candleStorage.findBeforeOrEqual(any(), any(), eq(1L))).thenReturn(List.of(onlyBar));

        PostOrderResponse posted = postBuy(onlyBar, OrderType.MARKET, 30, null);

        assertEquals(10, posted.getLotsExecuted());

        tickAt(currentTime);

        // What traded stands; the part still working is what stopped, so the order reads as
        // cancelled rather than as one still sitting in the market.
        assertEquals(ExecutionStatus.CANCELLED, stateOf(posted).getExecutionStatus());
        assertEquals(10, stateOf(posted).getLotsExecuted());
        assertEquals(10, freePositionLots());

        // The reservation for the lots that never filled is released, so nothing is left blocked.
        assertEquals(0, blockedPositionLots());

        // And it is no longer one of the account's working orders.
        assertTrue(orderService.get(GetOrdersRequest.of(testAccount.getId())).getOrders().isEmpty());
    }

    /**
     * The flattering assumption a zero latency makes is that a strategy reacting to a bar could
     * have traded inside that same bar. With a latency the order misses it: it reaches the market
     * afterwards and trades against whatever is there then, at that bar's price rather than the one
     * it was reacting to.
     */
    @Test
    public void testOrderReachingTheMarketLateTradesAgainstTheBarItArrivesIn() throws AbstractException {
        Candle postedBar = createCandle(currentTime, 10, 15, 5, 10, 100);
        Instant arrivalTime = currentTime.plus(Duration.ofMinutes(5));
        Candle arrivalBar = createCandle(arrivalTime, 12, 17, 11, 12, 100);

        orderService.setExecutionLatency(Duration.ofMinutes(5));
        addMoney(Quotation.of(100000));
        when(candleStorage.findAfterOrEqual(eq(config.getInstrument().getUid()), any(), eq(1L)))
            .thenReturn(List.of(arrivalBar));

        PostOrderResponse posted = postBuy(postedBar, OrderType.MARKET, 10, null);

        // Nothing traded yet - the order is only acknowledged.
        assertEquals(ExecutionStatus.NEW, posted.getExecutionStatus());
        assertEquals(0, posted.getLotsExecuted());
        assertEquals(0, freePositionLots());

        tickAt(arrivalTime);

        OrderState filled = stateOf(posted);

        assertEquals(ExecutionStatus.FILL, filled.getExecutionStatus());
        assertEquals(10, filled.getLotsExecuted());
        assertEquals(10, freePositionLots());

        // Filled at 12, the price when it got there - not the 10 it was placed against.
        Operation trade = operationRepository.getByAccountId(testAccount.getId(), TimeRange.MAX)
            .stream()
            .filter(operation -> operation.price() != null)
            .findFirst()
            .orElseThrow();

        assertEquals(arrivalBar.open(), trade.price());
    }

    /**
     * An order sized to the whole balance has no room for the price to move. Once part of it fills
     * and the market ticks up, the rest costs more than what is left on the account - which is a
     * thing markets do, not a broken simulation, so the order stops with what it got instead of
     * taking the run down with it.
     */
    @Test
    public void testRemainderTheAccountCanNoLongerAffordStopsTheOrderRatherThanTheRun() throws AbstractException {
        Candle firstBar = createCandle(currentTime, 10, 15, 5, 10, 100);
        // The same bar a minute later, but dearer: what is left of the order now costs more than
        // the fill left behind.
        Candle dearerBar = createCandle(currentTime.plus(Duration.ofMinutes(1)), 12, 17, 11, 12, 100);

        orderService.getLiquidityModel().setInfiniteLiquidity(false).setParticipationRate(0.1);

        // Exactly enough for thirty lots at the price the order is posted at, and not a kopek more.
        Quotation wholeBalance = firstBar.open()
            .multiply(30)
            .add(expectedCommissionCost(firstBar.open(), 30));

        addMoney(wholeBalance);
        when(candleStorage.findAfterOrEqual(eq(config.getInstrument().getUid()), any(), eq(1L)))
            .thenReturn(List.of(dearerBar));
        // Where the data ends, so a remainder with no further bar to trade against can be scheduled
        // to stop rather than run off the end of the history.
        when(candleStorage.findBeforeOrEqual(any(), any(), eq(1L))).thenReturn(List.of(dearerBar));

        PostOrderResponse posted = postBuy(firstBar, OrderType.MARKET, 30, null);

        // Ten lots at ten, and the remaining twenty reserved at the same price - which takes the
        // account down to nothing.
        assertEquals(10, posted.getLotsExecuted());
        assertEquals(ExecutionStatus.PARTIALLYFILL, posted.getExecutionStatus());

        tickAt(dearerBar.getTime());

        OrderState stopped = stateOf(posted);

        // Ten more traded at twelve, out of the freed reservation. The last ten would cost 120.36
        // and only 80.24 is left, so the order stops there rather than refusing what already stands.
        assertEquals(ExecutionStatus.CANCELLED, stopped.getExecutionStatus());
        assertEquals(20, stopped.getLotsExecuted());
        assertEquals(20, freePositionLots());

        // No longer working, so it drops out of the active orders.
        assertTrue(
            orderService.get(GetOrdersRequest.of(testAccount.getId())).getOrders().isEmpty()
        );

        // Nothing is left held back for an order that is done, and the account paid for exactly
        // the two fills that happened.
        Money blocked = broker.getOperationsService()
            .getAccountBalance(testAccount.getId())
            .getBlockedMoney(config.getInstrument().getCurrency());

        assertTrue(blocked == null || blocked.getQuotation().isEqual(Quotation.ZERO));

        Quotation spent = firstBar.open()
            .multiply(10)
            .add(expectedCommissionCost(firstBar.open(), 10))
            .add(dearerBar.open().multiply(10))
            .add(expectedCommissionCost(dearerBar.open(), 10));

        assertEquals(
            Money.of(config.getInstrument().getCurrency(), wholeBalance.subtract(spent)),
            broker.getOperationsService()
                .getAvailableMoney(testAccount.getId(), config.getInstrument().getCurrency())
        );
    }

    /**
     * A bar is one stretch of tape, and every order trading against it draws from the same budget.
     * Handing each order its own share independently would let the same volume be bought several
     * times over - the very fiction the liquidity cap exists to remove.
     */
    @Test
    public void testOrdersTradingOnOneBarShareWhatItTraded() throws AbstractException {
        Candle bar = createCandle(currentTime, 10, 15, 5, 10, 100);

        orderService.getLiquidityModel().setInfiniteLiquidity(false).setParticipationRate(0.1);
        addMoney(Quotation.of(100000));
        when(candleStorage.findAfterOrEqual(eq(config.getInstrument().getUid()), any(), eq(1L)))
            .thenReturn(List.of(bar));
        // Only the one bar exists, so a remainder looking past it finds where the data ends.
        when(candleStorage.findBeforeOrEqual(any(), any(), eq(1L))).thenReturn(List.of(bar));

        PostOrderResponse first = postBuy(bar, OrderType.MARKET, 30, null);

        // A tenth of the hundred lots the bar traded, and that is the whole of what it had.
        assertEquals(10, first.getLotsExecuted());

        PostOrderResponse second = postBuy(bar, OrderType.MARKET, 30, null);

        assertEquals(0, second.getLotsExecuted());
        assertEquals(ExecutionStatus.NEW, second.getExecutionStatus());

        // Ten lots bought in total - not twenty, which is what two orders each taking their own
        // tenth of the same bar would have produced.
        assertEquals(10, freePositionLots());
    }

    /**
     * While an order is working, the money for all of it is spoken for. Holding back only the next
     * bar's worth would leave the rest looking spendable, and a strategy sizing its next order by
     * the balance would commit the same money twice.
     */
    @Test
    public void testAWorkingOrderHoldsBackWhatTheWholeOfItWillCost() throws AbstractException {
        Candle bar = createCandle(currentTime, 10, 15, 5, 10, 100);

        orderService.getLiquidityModel().setInfiniteLiquidity(false).setParticipationRate(0.1);
        addMoney(Quotation.of(100000));
        when(candleStorage.findAfterOrEqual(eq(config.getInstrument().getUid()), any(), eq(1L)))
            .thenReturn(List.of(bar));
        // Only the one bar exists, so a remainder looking past it finds where the data ends.
        when(candleStorage.findBeforeOrEqual(any(), any(), eq(1L))).thenReturn(List.of(bar));

        String currency = config.getInstrument().getCurrency();
        Money before = broker.getOperationsService().getAvailableMoney(testAccount.getId(), currency);

        PostOrderResponse posted = postBuy(bar, OrderType.MARKET, 30, null);

        assertEquals(10, posted.getLotsExecuted());

        // Ten lots are paid for and twenty are still to come, and the account is short the price of
        // all thirty.
        Quotation wholeOrder = bar.open()
            .multiply(30)
            .add(expectedCommissionCost(bar.open(), 30));

        assertEquals(
            before.subtract(Money.of(currency, wholeOrder)),
            broker.getOperationsService().getAvailableMoney(testAccount.getId(), currency)
        );
    }

    protected void tickAt(Instant eventTime) {
        when(clock.currentTime()).thenReturn(eventTime);
        pendingOrderHandler().tick();
    }

    protected OrderState stateOf(PostOrderResponse posted) throws AbstractException {
        return orderService.getState(
            new GetOrderStateRequest()
                .setOrderId(posted.getOrderId())
                .setAccountId(testAccount.getId())
        );
    }

    /**
     * The search the handler is expected to run for the moment the order becomes fillable. The
     * search is the whole model of "the market reached my price", and a mock repository answers
     * whatever it was stubbed with regardless - so unless the arguments are checked, a search
     * looking on the wrong side of the limit passes every test.
     */
    protected FindPriceParams priceSearch(
        Quotation price,
        CandlePriceField priceField,
        ComparisonOperator comparisonOperator
    ) {
        return new FindPriceParams(
            config.getInstrument().getUid(),
            currentTime,
            lifeTimeEnd(),
            price,
            priceField,
            comparisonOperator,
            1
        );
    }

    /**
     * Runs the simulation up to the moment the parked order was scheduled for, then checks it
     * filled and at what price - read back from the trade operation, which is what the fill leaves
     * behind for the account holder.
     */
    protected void assertFillsAt(
        PostOrderResponse parked,
        Quotation expectedPrice,
        OrderDirection direction
    ) throws AbstractException {
        when(clock.currentTime()).thenReturn(triggerTime());
        pendingOrderHandler().tick();

        OrderState state = orderService.getState(
            new GetOrderStateRequest()
                .setOrderId(parked.getOrderId())
                .setAccountId(testAccount.getId())
        );

        assertEquals(ExecutionStatus.FILL, state.getExecutionStatus());
        assertEquals(parked.getLotsRequested(), state.getLotsExecuted());

        List<Operation> trades = operationRepository.getByAccountId(testAccount.getId(), TimeRange.MAX)
            .stream()
            .filter(operation -> operation.state() == OperationState.EXECUTED)
            .filter(operation -> operation.price() != null)
            .toList();

        assertEquals(1, trades.size());
        assertEquals(direction.isBuy(), trades.getFirst().direction().isBuy());
        assertEquals(expectedPrice, trades.getFirst().price());
    }

    protected Instant triggerTime() {
        return currentTime.plus(Duration.ofMinutes(10));
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
        when(candleStorage.findByPrice(any()))
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
        when(candleStorage.findByPrice(any())).thenReturn(List.of(buySignalCandle));

        PostOrderResponse parked = assertBuyParked(validCandle, 10, Quotation.of(9));

        List<OrderState> activeOrders = orderService
            .get(GetOrdersRequest.of(testAccount.getId()))
            .getOrders();

        assertEquals(1, activeOrders.size());
        assertEquals(parked.getOrderId(), activeOrders.getFirst().getOrderId());
        assertEquals(ExecutionStatus.NEW, activeOrders.getFirst().getExecutionStatus());
    }
}
