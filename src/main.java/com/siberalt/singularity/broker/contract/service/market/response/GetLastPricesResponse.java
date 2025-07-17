package com.siberalt.singularity.broker.contract.service.market.response;

import java.util.List;

public class GetLastPricesResponse {
    List<LastPrice> prices;

    public List<LastPrice> getPrices() {
        return prices;
    }

    public GetLastPricesResponse setPrices(List<LastPrice> prices) {
        this.prices = prices;
        return this;
    }
}
