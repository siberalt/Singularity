package investtech.broker.contract.service.instrument;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.instrument.request.GetRequest;
import investtech.broker.contract.service.instrument.response.GetResponse;

public interface InstrumentServiceInterface {
    GetResponse get(GetRequest request) throws AbstractException;
}
