package investtech.broker.contract.service.market.response;

import java.util.Collection;

public class GetCandlesResponse {
    Collection<HistoricCandle> candles;

    public Collection<HistoricCandle> getCandles() {
        return candles;
    }

    public GetCandlesResponse setCandles(Collection<HistoricCandle> candles) {
        this.candles = candles;
        return this;
    }
}
