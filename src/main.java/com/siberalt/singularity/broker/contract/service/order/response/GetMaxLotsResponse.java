package com.siberalt.singularity.broker.contract.service.order.response;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

public record GetMaxLotsResponse(
    BuyLimits buyLimits,
    SellLimits sellLimits,
    BuyLimits buyMarginLimits,
    SellLimits sellMarginLimits,
    String currency
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private BuyLimits buyLimits;
        private SellLimits sellLimits;
        private BuyLimits buyMarginLimits;
        private SellLimits sellMarginLimits;
        private String currency;

        public Builder buyLimits(BuyLimits buyLimits) {
            this.buyLimits = buyLimits;
            return this;
        }

        public Builder sellLimits(SellLimits sellLimits) {
            this.sellLimits = sellLimits;
            return this;
        }

        public Builder buyMarginLimits(BuyLimits buyMarginLimits) {
            this.buyMarginLimits = buyMarginLimits;
            return this;
        }

        public Builder sellMarginLimits(SellLimits sellMarginLimits) {
            this.sellMarginLimits = sellMarginLimits;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public GetMaxLotsResponse build() {
            return new GetMaxLotsResponse(buyLimits, sellLimits, buyMarginLimits, sellMarginLimits, currency);
        }
    }

    /**
     * Limits for buy operations - contains buy/sell money amount and lot limits
     */
    public record BuyLimits(
        Long buyMaxLots,
        Long buyMaxMarketLots,
        Quotation buyMoneyAmount
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Long buyMaxLots;
            private Long buyMaxMarketLots;
            private Quotation buyMoneyAmount;

            public Builder buyMaxLots(Long buyMaxLots) {
                this.buyMaxLots = buyMaxLots;
                return this;
            }

            public Builder buyMaxMarketLots(Long buyMaxMarketLots) {
                this.buyMaxMarketLots = buyMaxMarketLots;
                return this;
            }

            public Builder buyMoneyAmount(Quotation buyMoneyAmount) {
                this.buyMoneyAmount = buyMoneyAmount;
                return this;
            }

            public BuyLimits build() {
                return new BuyLimits(buyMaxLots, buyMaxMarketLots, buyMoneyAmount);
            }
        }
    }

    /**
     * Limits for sell operations - contains only sell max lots
     */
    public record SellLimits(
        Long sellMaxLots
    ) {
    }
}
