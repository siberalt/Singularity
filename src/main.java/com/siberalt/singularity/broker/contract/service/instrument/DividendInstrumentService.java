package com.siberalt.singularity.broker.contract.service.instrument;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetDividendsRequest;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetDividendsResponse;

/**
 * Sub-contract of {@link InstrumentService} for brokers that can report dividend payments; kept
 * separate so brokers without dividend data (e.g. the mock one) need not stub it.
 */
public interface DividendInstrumentService extends InstrumentService {
    GetDividendsResponse getDividends(GetDividendsRequest request) throws AbstractException;
}
