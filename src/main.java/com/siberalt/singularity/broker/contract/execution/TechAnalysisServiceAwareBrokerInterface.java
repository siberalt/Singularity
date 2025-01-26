package com.siberalt.singularity.broker.contract.execution;

import com.siberalt.singularity.broker.contract.service.market.TechAnalysisServiceInterface;

public interface TechAnalysisServiceAwareBrokerInterface extends BrokerInterface{
    TechAnalysisServiceInterface getTechAnalysisService();
}
