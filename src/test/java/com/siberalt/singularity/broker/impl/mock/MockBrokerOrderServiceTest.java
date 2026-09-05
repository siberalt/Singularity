package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.strategy.context.Clock;
import org.junit.jupiter.api.Test;


/**
 * The order service as the plain {@link MockBroker} builds it, whose clock does not advance: an
 * order the market is not ready for is refused instead of parked. Everything else is expected to
 * behave exactly as it does for {@link EventMockBroker}, which is what makes running the same suite
 * against both worth doing.
 */
public class MockBrokerOrderServiceTest extends AbstractMockOrderServiceTest {
    @Override
    MockBroker createBroker(
        ReadCandleRepository candleStorage,
        ReadInstrumentRepository instrumentStorage,
        OrderRepository orderRepository,
        OperationRepository operationRepository,
        Clock clock
    ) {
        return new MockBroker(candleStorage, instrumentStorage, orderRepository, operationRepository, clock);
    }

    @Test
    public void testBuyLimitRefusedWhenMarketIsNotReady() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addMoney(validCandle.open().multiply(100));

        assertBuyRefused(validCandle, 10, Quotation.of(9));
    }

    @Test
    public void testSellLimitRefusedWhenMarketIsNotReady() throws AbstractException {
        Candle validCandle = createCandle(
            currentTime, 10, 15, 5, 10, 100
        );

        addInstruments(80);

        assertSellRefused(validCandle, 10, Quotation.of(11));
    }
}
