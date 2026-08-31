package com.siberalt.singularity.broker.impl.tinkoff.sandbox;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetTradableRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class InstrumentServiceIT extends AbstractTinkoffSanboxIT {
    @Test
    public void getTradableReturnsOnlyRubShares() throws IOException, AbstractException {
        var instrumentService = getTinkoffSandbox().getInstrumentService();

        var instruments = instrumentService.getTradable(GetTradableRequest.of("rub")).getInstruments();

        Assertions.assertFalse(instruments.isEmpty());
        instruments.forEach(instrument ->
            Assertions.assertEquals("rub", instrument.getCurrency().toLowerCase())
        );
    }
}
