package investtech.broker.impl.mock;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.order.request.CancelOrderRequest;
import investtech.broker.contract.service.order.request.OrderDirection;
import investtech.broker.contract.service.order.request.OrderType;
import investtech.broker.contract.service.order.request.PostOrderRequest;
import investtech.broker.contract.service.order.response.ExecutionStatus;
import investtech.broker.contract.service.order.response.PostOrderResponse;
import investtech.broker.contract.value.money.Money;
import investtech.broker.contract.value.quotation.Quotation;
import investtech.broker.impl.mock.config.InstrumentConfig;
import investtech.simulation.shared.market.candle.Candle;
import investtech.simulation.shared.market.candle.ComparisonOperator;
import investtech.simulation.shared.market.candle.FindPriceParams;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class EventMockOrderServiceTest extends MockOrderServiceTest {
    @Test
    public void testBuyLimit() throws AbstractException {
        InstrumentConfig instrumentConfig = config.getInstrument();
        var ordersService = broker.getOrderService();

        Candle validCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(10))
            .setHighPrice(Quotation.of(15))
            .setLowPrice(Quotation.of(5))
            .setOpenPrice(Quotation.of(10))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());
        Candle invalidCandle = new Candle()
            .setTime(currentTime)
            .setClosePrice(Quotation.of(1))
            .setHighPrice(Quotation.of(1))
            .setLowPrice(Quotation.of(1))
            .setOpenPrice(Quotation.of(1))
            .setVolume(100)
            .setInstrumentUid(instrumentConfig.getUid());
        FindPriceParams validParams = new FindPriceParams()
            .setFrom(currentTime)
            .setTo(currentTime.plus(ordersService.getLimitOrderLifeTime()))
            .setComparisonOperator(ComparisonOperator.MORE_OR_EQUAL)
            .setMaxCount(1)
            .setPrice(validCandle.getOpenPrice())
            .setInstrumentUid(instrumentConfig.getUid());

        when(candleStorage.getAt(any(), any()))
            .thenReturn(Optional.of(invalidCandle));
        when(candleStorage.getAt(instrumentConfig.getUid(), currentTime))
            .thenReturn(Optional.of(validCandle));
        when(candleStorage.findByOpenPrice(validParams))
            .thenReturn(List.of(validCandle));

        broker.getOperationsService().addMoney(
            testAccount.getId(),
            Money.of(
                config.getInstrument().getCurrency(),
                validCandle.getOpenPrice().multiply(100)
            )
        );

        PostOrderResponse postResponse = ordersService.post(
            new PostOrderRequest()
                .setInstrumentId(instrumentConfig.getUid())
                .setQuantity(10)
                .setPrice(validCandle.getOpenPrice())
                .setAccountId(testAccount.getId())
                .setDirection(OrderDirection.BUY)
                .setOrderType(OrderType.LIMIT)
        );

        assertNotNull(postResponse.getOrderId());
        assertEquals(0, postResponse.getLotsExecuted());
        assertEquals(10, postResponse.getLotsRequested());
        assertEquals(OrderDirection.BUY, postResponse.getDirection());
        assertEquals(instrumentConfig.getUid(), postResponse.getInstrumentUid());
        assertEquals(Quotation.ZERO, postResponse.getTotalPrice().getQuotation());
        assertEquals(Quotation.ZERO, postResponse.getTotalPricePerOne().getQuotation());
        assertEquals(validCandle.getOpenPrice().multiply(10), postResponse.getInitialPrice().getQuotation());
        assertEquals(validCandle.getOpenPrice(), postResponse.getInitialPricePerOne().getQuotation());
        assertEquals(ExecutionStatus.NEW, postResponse.getExecutionStatus());

        verify(candleStorage, times(1)).getAt(instrumentConfig.getUid(), currentTime);
    }

    private void testCancelOrder(PostOrderResponse postResponse) throws AbstractException {
        var ordersService = broker.getOrderService();

        var cancelResponse = ordersService.cancel(
            new CancelOrderRequest()
                .setOrderId(postResponse.getOrderId())
                .setAccountId(testAccount.getId())
        );

        assertNotNull(cancelResponse);
    }
}
