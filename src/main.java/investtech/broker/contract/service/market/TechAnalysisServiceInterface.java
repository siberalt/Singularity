package investtech.broker.contract.service.market;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.market.request.GetTechAnalysisRequest;
import investtech.broker.contract.service.market.response.GetTechAnalysisResponse;

public interface TechAnalysisServiceInterface  {
    GetTechAnalysisResponse getTechAnalysis(GetTechAnalysisRequest request) throws AbstractException;
}
