package com.siberalt.singularity.broker.contract.service.market.response;

import java.util.List;

public class GetLastPricesResponse {
    List<LastPrice> lastPrices;

    public List<LastPrice> getLastPrices() {
        return lastPrices;
    }

    public GetLastPricesResponse setLastPrices(List<LastPrice> lastPrices) {
        this.lastPrices = lastPrices;
        return this;
    }
}
