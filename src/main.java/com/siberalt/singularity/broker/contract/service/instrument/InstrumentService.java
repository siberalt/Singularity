package com.siberalt.singularity.broker.contract.service.instrument;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetTradableRequest;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetResponse;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetTradableResponse;

public interface InstrumentService {
    GetResponse get(GetRequest request) throws AbstractException;

    GetTradableResponse getTradable(GetTradableRequest request) throws AbstractException;
}
