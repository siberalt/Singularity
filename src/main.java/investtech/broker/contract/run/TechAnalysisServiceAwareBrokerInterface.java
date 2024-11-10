package investtech.broker.contract.run;

import investtech.broker.contract.service.market.TechAnalysisServiceInterface;

public interface TechAnalysisServiceAwareBrokerInterface extends BrokerInterface{
    TechAnalysisServiceInterface getTechAnalysisService();
}
