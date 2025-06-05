package com.siberalt.singularity.broker.contract.service.market;


import com.siberalt.singularity.broker.contract.service.market.request.GetCandlesRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetLastPricesRequest;
import com.siberalt.singularity.broker.contract.service.market.response.GetLastPricesResponse;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.market.response.GetCandlesResponse;


public interface MarketDataService {
    GetCandlesResponse getCandles(GetCandlesRequest request) throws AbstractException;

    GetLastPricesResponse getLastPrices(GetLastPricesRequest request) throws AbstractException;
}
