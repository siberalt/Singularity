package investtech.broker.contract.service.market;


import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.market.request.GetCandlesRequest;
import investtech.broker.contract.service.market.request.GetLastPricesRequest;
import investtech.broker.contract.service.market.response.GetCandlesResponse;
import investtech.broker.contract.service.market.response.GetLastPricesResponse;


public interface MarketDataServiceInterface {
    GetCandlesResponse getCandles(GetCandlesRequest request) throws AbstractException;

    GetLastPricesResponse getLastPrices(GetLastPricesRequest request) throws AbstractException;
}
