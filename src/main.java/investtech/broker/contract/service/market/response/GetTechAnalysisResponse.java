package investtech.broker.contract.service.market.response;

import java.util.Collection;

public class GetTechAnalysisResponse {
    Collection<TechAnalysisItem> technicalIndicators;

    public Collection<TechAnalysisItem> getTechnicalIndicators() {
        return technicalIndicators;
    }

    public GetTechAnalysisResponse setTechnicalIndicators(Collection<TechAnalysisItem> technicalIndicators) {
        this.technicalIndicators = technicalIndicators;
        return this;
    }
}
