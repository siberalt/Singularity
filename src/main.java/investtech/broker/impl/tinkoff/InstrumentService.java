package investtech.broker.impl.tinkoff;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.instrument.InstrumentServiceInterface;
import investtech.broker.contract.service.instrument.request.GetRequest;
import investtech.broker.contract.service.instrument.response.GetResponse;
import investtech.broker.impl.tinkoff.exception.ExceptionConverter;
import investtech.broker.impl.tinkoff.translation.InstrumentTranslator;
import ru.tinkoff.piapi.core.InstrumentsService;

public class InstrumentService implements InstrumentServiceInterface {
    protected InstrumentsService instrumentsService;

    public InstrumentService(InstrumentsService instrumentsService) {
        this.instrumentsService = instrumentsService;
    }

    @Override
    public GetResponse get(GetRequest request) throws AbstractException {
        var findInstrumentResponse = ExceptionConverter.rethrowContractExceptionOnError(
                () -> instrumentsService.findInstrumentSync(request.getId())
        );
        var instrument = findInstrumentResponse.stream().findFirst().orElse(null);
        var getResponse = new GetResponse();

        if (null != instrument) {
            var getInstrumentResponse = ExceptionConverter.rethrowContractExceptionOnError(
                    () -> instrumentsService.getInstrumentByTickerSync(instrument.getTicker(), instrument.getClassCode())
            );

            getResponse.setInstrument(
                    InstrumentTranslator.toContract(instrument)
                            .setLot(getInstrumentResponse.getLot())
                            .setIsin(getInstrumentResponse.getIsin())
                            .setCurrency(getInstrumentResponse.getCurrency())
            );
        }

        return getResponse;
    }
}
