package com.siberalt.singularity.broker.contract.service.market.response;

import java.util.List;

public class GetCandlesResponse {
    List<HistoricCandle> candles;

    public List<HistoricCandle> getCandles() {
        return candles;
    }

    public GetCandlesResponse setCandles(List<HistoricCandle> candles) {
        this.candles = candles;
        return this;
    }
}
