package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetTradableRequest;
import com.siberalt.singularity.entity.instrument.Instrument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.SharesResponse;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstrumentServiceTest {
    @Test
    void getTradableFiltersByCurrencyAndTradeAvailability() throws AbstractException {
        Share tradableRub = Share.newBuilder()
            .setUid("uid-rub")
            .setIsin("RU000A0000A0")
            .setName("RUB Tradable Share")
            .setLot(10)
            .setCurrency("rub")
            .setApiTradeAvailableFlag(true)
            .build();
        Share tradableUsd = Share.newBuilder()
            .setUid("uid-usd")
            .setIsin("US0000000001")
            .setName("USD Tradable Share")
            .setLot(1)
            .setCurrency("usd")
            .setApiTradeAvailableFlag(true)
            .build();
        Share notTradableRub = Share.newBuilder()
            .setUid("uid-not-tradable")
            .setIsin("RU000A0000B1")
            .setName("Not Tradable Share")
            .setLot(1)
            .setCurrency("rub")
            .setApiTradeAvailableFlag(false)
            .build();

        InstrumentsServiceGrpc.InstrumentsServiceBlockingStub stub = mock(InstrumentsServiceGrpc.InstrumentsServiceBlockingStub.class);
        when(stub.shares(any(InstrumentsRequest.class))).thenReturn(
            SharesResponse.newBuilder()
                .addAllInstruments(List.of(tradableRub, tradableUsd, notTradableRub))
                .build()
        );

        InstrumentService instrumentService = new InstrumentService(stub);

        List<Instrument> instruments = instrumentService.getTradable(GetTradableRequest.of("rub")).getInstruments();

        Assertions.assertEquals(1, instruments.size());
        Instrument instrument = instruments.getFirst();
        Assertions.assertEquals("uid-rub", instrument.getUid());
        Assertions.assertEquals("RU000A0000A0", instrument.getIsin());
        Assertions.assertEquals("RUB Tradable Share", instrument.getName());
        Assertions.assertEquals(10, instrument.getLot());
        Assertions.assertEquals("rub", instrument.getCurrency());
        Assertions.assertEquals(InstrumentType.SHARE, instrument.getInstrumentType());
    }

    @Test
    void getTradableReturnsAllCurrenciesWhenNoFilterGiven() throws AbstractException {
        Share rubShare = Share.newBuilder()
            .setUid("uid-rub")
            .setCurrency("rub")
            .setApiTradeAvailableFlag(true)
            .build();
        Share usdShare = Share.newBuilder()
            .setUid("uid-usd")
            .setCurrency("usd")
            .setApiTradeAvailableFlag(true)
            .build();

        InstrumentsServiceGrpc.InstrumentsServiceBlockingStub stub = mock(InstrumentsServiceGrpc.InstrumentsServiceBlockingStub.class);
        when(stub.shares(any(InstrumentsRequest.class))).thenReturn(
            SharesResponse.newBuilder().addAllInstruments(List.of(rubShare, usdShare)).build()
        );

        InstrumentService instrumentService = new InstrumentService(stub);

        List<Instrument> instruments = instrumentService.getTradable(new GetTradableRequest()).getInstruments();

        Assertions.assertEquals(2, instruments.size());
    }
}
