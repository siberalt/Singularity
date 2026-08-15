package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetResponse;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.InstrumentTranslator;
import ru.tinkoff.piapi.contract.v1.FindInstrumentRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentIdType;
import ru.tinkoff.piapi.contract.v1.InstrumentRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;

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
}
