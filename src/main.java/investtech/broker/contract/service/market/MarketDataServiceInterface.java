package investtech.broker.contract.service.market;


import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.market.request.GetCandlesRequest;
import investtech.broker.contract.service.market.request.GetLastPricesRequest;
import investtech.broker.contract.service.market.response.GetLastPricesResponse;
import investtech.broker.contract.service.market.request.GetTechAnalysisRequest;
import investtech.broker.contract.service.market.response.GetCandlesResponse;
import investtech.broker.contract.service.market.response.GetTechAnalysisResponse;


public interface MarketDataServiceInterface {
    GetCandlesResponse getCandles(GetCandlesRequest request) throws AbstractException;

    GetTechAnalysisResponse getTechAnalysis(GetTechAnalysisRequest request) throws AbstractException;

    GetLastPricesResponse getLastPrices(GetLastPricesRequest request) throws AbstractException;
}
