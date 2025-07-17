package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.entity.instrument.InstrumentRepository;
import com.siberalt.singularity.strategy.context.simulation.time.ClockStub;
import com.siberalt.singularity.strategy.simulation.SimulationContext;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.entity.instrument.InMemoryInstrumentRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class MockInstrumentServiceTest {
    @Test
    void testBasic() throws AbstractException {
        var instrumentUid = UUID.randomUUID().toString();
        var instrumentPositionUid = UUID.randomUUID().toString();
        var instrumentType = InstrumentType.SHARE;
        var instrumentLot = 10;
        var instrumentIsin = "RU102";
        var instrumentCurrency = "RUB";

        InstrumentRepository instrumentRepository = new InMemoryInstrumentRepository();
        instrumentRepository.save(
            new Instrument()
                .setInstrumentType(instrumentType)
                .setLot(instrumentLot)
                .setIsin(instrumentIsin)
                .setCurrency(instrumentCurrency)
                .setUid(instrumentUid)
                .setPositionUid(instrumentPositionUid)
        );

        var mockBroker = new MockBroker(null, instrumentRepository, null, new ClockStub());
        var instrumentService = mockBroker.getInstrumentService();

        var response = instrumentService.get(GetRequest.of(instrumentUid));
        var instrument = response.getInstrument();

        Assertions.assertNotNull(instrument);
        Assertions.assertEquals(instrumentType, instrument.getInstrumentType());
        Assertions.assertEquals(instrumentLot, instrument.getLot());
        Assertions.assertEquals(instrumentIsin, instrument.getIsin());
        Assertions.assertEquals(instrumentCurrency, instrument.getCurrency());
        Assertions.assertEquals(instrumentUid, instrument.getUid());
        Assertions.assertEquals(instrumentPositionUid, instrument.getPositionUid());

        response = instrumentService.get(GetRequest.of(UUID.randomUUID().toString()));
        Assertions.assertNull(response.getInstrument());
    }
}
