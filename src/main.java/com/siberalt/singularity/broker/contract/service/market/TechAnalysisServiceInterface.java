package com.siberalt.singularity.broker.contract.service.market;

import com.siberalt.singularity.broker.contract.service.market.request.GetTechAnalysisRequest;
import com.siberalt.singularity.broker.contract.service.market.response.GetTechAnalysisResponse;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;

public interface TechAnalysisServiceInterface  {
    GetTechAnalysisResponse getTechAnalysis(GetTechAnalysisRequest request) throws AbstractException;
}
