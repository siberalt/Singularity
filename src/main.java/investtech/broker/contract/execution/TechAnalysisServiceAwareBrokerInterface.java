package investtech.broker.contract.execution;

import investtech.broker.contract.service.market.TechAnalysisServiceInterface;

public interface TechAnalysisServiceAwareBrokerInterface extends BrokerInterface{
    TechAnalysisServiceInterface getTechAnalysisService();
}
