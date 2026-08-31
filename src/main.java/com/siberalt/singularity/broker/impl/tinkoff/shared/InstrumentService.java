package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetTradableRequest;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetResponse;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetTradableResponse;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.InstrumentTranslator;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.ShareTranslator;
import com.siberalt.singularity.entity.instrument.Instrument;
import ru.tinkoff.piapi.contract.v1.FindInstrumentRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentIdType;
import ru.tinkoff.piapi.contract.v1.InstrumentRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentStatus;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.Share;

import java.util.List;
import java.util.stream.Collectors;

public class InstrumentService implements com.siberalt.singularity.broker.contract.service.instrument.InstrumentService {
    protected InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsService;

    public InstrumentService(InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsService) {
        this.instrumentsService = instrumentsService;
    }

    @Override
    public GetResponse get(GetRequest request) throws AbstractException {
        var findInstrumentResponse = ExceptionConverter.rethrowContractExceptionOnError(
            () -> instrumentsService.findInstrument(FindInstrumentRequest.newBuilder().setQuery(request.getId()).build())
        );
        var instrument = findInstrumentResponse.getInstrumentsList().stream().findFirst().orElse(null);
        var getResponse = new GetResponse();

        if (null != instrument) {
            var getInstrumentResponse = ExceptionConverter.rethrowContractExceptionOnError(
                () -> instrumentsService.getInstrumentBy(InstrumentRequest.newBuilder()
                    .setId(instrument.getTicker())
                    .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_TICKER)
                    .setClassCode(instrument.getClassCode())
                    .build()
                )
            );
            var instrumentShort = getInstrumentResponse.getInstrument();

            getResponse.setInstrument(
                InstrumentTranslator.toContract(instrument)
                    .setLot(instrumentShort.getLot())
                    .setIsin(instrumentShort.getIsin())
                    .setCurrency(instrumentShort.getCurrency())
            );
        }

        return getResponse;
    }

    @Override
    public GetTradableResponse getTradable(GetTradableRequest request) throws AbstractException {
        var sharesResponse = ExceptionConverter.rethrowContractExceptionOnError(
            () -> instrumentsService.shares(
                InstrumentsRequest.newBuilder()
                    .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                    .build()
            )
        );

        List<Instrument> instruments = sharesResponse.getInstrumentsList().stream()
            .filter(Share::getApiTradeAvailableFlag)
            .filter(share -> request.getCurrency() == null || share.getCurrency().equalsIgnoreCase(request.getCurrency()))
            .map(ShareTranslator::toContract)
            .collect(Collectors.toList());

        return new GetTradableResponse().setInstruments(instruments);
    }
}
