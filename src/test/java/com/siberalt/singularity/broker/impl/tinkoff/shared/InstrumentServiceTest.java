package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetDividendsRequest;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetTradableRequest;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.TimestampTranslator;
import com.siberalt.singularity.entity.instrument.Instrument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.tinkoff.piapi.contract.v1.Dividend;
import ru.tinkoff.piapi.contract.v1.GetDividendsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.tinkoff.piapi.contract.v1.SharesResponse;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    @Test
    void getDividendsPassesRequestAndTranslatesResponse() throws AbstractException {
        Instant from = Instant.parse("2024-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-01T00:00:00Z");
        Instant paymentDate = Instant.parse("2024-07-20T00:00:00Z");
        Instant recordDate = Instant.parse("2024-07-18T00:00:00Z");

        InstrumentsServiceGrpc.InstrumentsServiceBlockingStub stub = mock(InstrumentsServiceGrpc.InstrumentsServiceBlockingStub.class);
        when(stub.getDividends(any(ru.tinkoff.piapi.contract.v1.GetDividendsRequest.class))).thenReturn(
            GetDividendsResponse.newBuilder()
                .addDividends(
                    Dividend.newBuilder()
                        .setDividendNet(MoneyValue.newBuilder().setCurrency("rub").setUnits(12).setNano(500_000_000))
                        .setPaymentDate(TimestampTranslator.toTinkoff(paymentDate))
                        .setRecordDate(TimestampTranslator.toTinkoff(recordDate))
                        .setDividendType("Regular Cash")
                        .setRegularity("Annual")
                        .setClosePrice(MoneyValue.newBuilder().setCurrency("rub").setUnits(300))
                        .setYieldValue(Quotation.newBuilder().setUnits(4).setNano(170_000_000))
                )
                .build()
        );

        InstrumentService instrumentService = new InstrumentService(stub);

        List<com.siberalt.singularity.broker.contract.service.instrument.common.Dividend> dividends =
            instrumentService.getDividends(GetDividendsRequest.of("uid-1", from, to)).getDividends();

        ArgumentCaptor<ru.tinkoff.piapi.contract.v1.GetDividendsRequest> captor =
            ArgumentCaptor.forClass(ru.tinkoff.piapi.contract.v1.GetDividendsRequest.class);
        verify(stub).getDividends(captor.capture());
        Assertions.assertEquals("uid-1", captor.getValue().getInstrumentId());
        Assertions.assertEquals(from, TimestampTranslator.toContract(captor.getValue().getFrom()));
        Assertions.assertEquals(to, TimestampTranslator.toContract(captor.getValue().getTo()));

        Assertions.assertEquals(1, dividends.size());
        var dividend = dividends.getFirst();
        Assertions.assertEquals("rub", dividend.getDividendNet().getCurrencyIso());
        Assertions.assertEquals(12, dividend.getDividendNet().getQuotation().getUnits());
        Assertions.assertEquals(500_000_000, dividend.getDividendNet().getQuotation().getNano());
        Assertions.assertEquals(paymentDate, dividend.getPaymentDate());
        Assertions.assertEquals(recordDate, dividend.getRecordDate());
        Assertions.assertNull(dividend.getDeclaredDate());
        Assertions.assertEquals("Regular Cash", dividend.getDividendType());
        Assertions.assertEquals("Annual", dividend.getRegularity());
        Assertions.assertEquals(300, dividend.getClosePrice().getQuotation().getUnits());
        Assertions.assertEquals(4, dividend.getYieldValue().getUnits());
        Assertions.assertEquals(170_000_000, dividend.getYieldValue().getNano());
    }

    @Test
    void getDividendsReturnsEmptyListWhenNoneFound() throws AbstractException {
        InstrumentsServiceGrpc.InstrumentsServiceBlockingStub stub = mock(InstrumentsServiceGrpc.InstrumentsServiceBlockingStub.class);
        when(stub.getDividends(any(ru.tinkoff.piapi.contract.v1.GetDividendsRequest.class)))
            .thenReturn(GetDividendsResponse.getDefaultInstance());

        var response = new InstrumentService(stub).getDividends(
            GetDividendsRequest.of("uid-1", Instant.EPOCH, Instant.parse("2025-01-01T00:00:00Z"))
        );

        Assertions.assertTrue(response.getDividends().isEmpty());
    }
}
