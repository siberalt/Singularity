package com.siberalt.singularity.broker.contract.service.instrument;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetResponse;

public interface InstrumentService {
    GetResponse get(GetRequest request) throws AbstractException;
}
