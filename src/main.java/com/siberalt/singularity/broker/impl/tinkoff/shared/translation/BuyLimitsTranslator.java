package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.order.response.GetMaxLotsResponse;
import ru.tinkoff.piapi.contract.v1.GetMaxLotsResponse.BuyLimitsView;

public class BuyLimitsTranslator {
    private BuyLimitsTranslator() {
    }

    public static GetMaxLotsResponse.BuyLimits toContract(BuyLimitsView buyLimits) {
        return GetMaxLotsResponse.BuyLimits.builder()
            .buyMaxLots(buyLimits.getBuyMaxLots())
            .buyMaxMarketLots(buyLimits.getBuyMaxMarketLots())
            .buyMoneyAmount(QuotationTranslator.toContract(buyLimits.getBuyMoneyAmount()))
            .build();
    }

    public static BuyLimitsView toTinkoff(GetMaxLotsResponse.BuyLimits buyLimits) {
        return BuyLimitsView.newBuilder()
            .setBuyMaxLots(buyLimits.buyMaxLots())
            .setBuyMoneyAmount(QuotationTranslator.toTinkoff(buyLimits.buyMoneyAmount()))
            .setBuyMaxMarketLots(buyLimits.buyMaxMarketLots())
            .build();
    }
}
