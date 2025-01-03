package investtech.broker.impl.mock;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.instrument.Instrument;
import investtech.broker.contract.service.instrument.common.InstrumentType;
import investtech.broker.contract.service.instrument.request.GetRequest;
import investtech.simulation.shared.instrument.RuntimeInstrumentStorage;
import investtech.strategy.context.emulation.SimulationContext;
import investtech.strategy.context.emulation.time.SimulationTimeSynchronizer;
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

        var instrumentStorage = new RuntimeInstrumentStorage().add(
                new Instrument()
                        .setInstrumentType(instrumentType)
                        .setLot(instrumentLot)
                        .setIsin(instrumentIsin)
                        .setCurrency(instrumentCurrency)
                        .setUid(instrumentUid)
                        .setPositionUid(instrumentPositionUid)
        );

        var mockBroker = new MockBroker(null, instrumentStorage);
        var instrumentService = mockBroker.getInstrumentService();
        mockBroker.applyContext(new SimulationContext(null, null, new SimulationTimeSynchronizer()));

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
