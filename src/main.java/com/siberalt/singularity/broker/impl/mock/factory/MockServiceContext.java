package com.siberalt.singularity.broker.impl.mock.factory;

import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.instrument.ReadInstrumentRepository;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.order.OrderRepository;
import com.siberalt.singularity.strategy.context.Clock;

/**
 * The inputs every mock-broker service is built from - the mock-broker analogue of Tinkoff's
 * {@code ServiceStubFactory}. Handed to one {@link MockServiceFactory} (or a service-specific
 * factory) per service by {@link MockServicesFactory}.
 */
public record MockServiceContext(
    Clock clock,
    String brokerId,
    ReadCandleRepository candleRepository,
    ReadInstrumentRepository instrumentRepository,
    OrderRepository orderRepository,
    OperationRepository operationRepository,
    double commissionRatio
) {
}
