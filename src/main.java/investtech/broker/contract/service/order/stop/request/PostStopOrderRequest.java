package investtech.broker.contract.service.order.stop.request;

import investtech.broker.contract.service.order.common.ExchangeOrderType;
import investtech.broker.contract.service.order.request.PriceType;
import investtech.broker.contract.value.quatation.Quotation;
import java.time.Instant;

public class PostStopOrderRequest {
    public static class TrailingData {
        protected Quotation indent;
        protected TrailingValueType indentType;
        protected Quotation spread;
        protected TrailingValueType spreadType;

        public Quotation getIndent() {
            return indent;
        }

        public PostStopOrderRequest.TrailingData setIndent(Quotation indent) {
            this.indent = indent;
            return this;
        }

        public TrailingValueType getIndentType() {
            return indentType;
        }

        public PostStopOrderRequest.TrailingData setIndentType(TrailingValueType indentType) {
            this.indentType = indentType;
            return this;
        }

        public Quotation getSpread() {
            return spread;
        }

        public PostStopOrderRequest.TrailingData setSpread(Quotation spread) {
            this.spread = spread;
            return this;
        }

        public TrailingValueType getSpreadType() {
            return spreadType;
        }

        public PostStopOrderRequest.TrailingData setSpreadType(TrailingValueType spreadType) {
            this.spreadType = spreadType;
            return this;
        }
    }

    protected long quantity;
    protected Quotation price;
    protected Quotation stopPrice;
    protected StopOrderDirection direction;
    protected String accountId;
    protected StopOrderExpirationType expirationType;
    protected StopOrderType stopOrderType;
    protected Instant expireDate;
    protected String instrumentId;
    protected ExchangeOrderType exchangeOrderType;
    protected TakeProfitType takeProfitType;
    protected PostStopOrderRequest.TrailingData trailingData;
    protected PriceType priceType;
    protected String orderId;

    public long getQuantity() {
        return quantity;
    }

    public PostStopOrderRequest setQuantity(long quantity) {
        this.quantity = quantity;
        return this;
    }

    public Quotation getPrice() {
        return price;
    }

    public PostStopOrderRequest setPrice(Quotation price) {
        this.price = price;
        return this;
    }

    public Quotation getStopPrice() {
        return stopPrice;
    }

    public PostStopOrderRequest setStopPrice(Quotation stopPrice) {
        this.stopPrice = stopPrice;
        return this;
    }

    public StopOrderDirection getDirection() {
        return direction;
    }

    public PostStopOrderRequest setDirection(StopOrderDirection direction) {
        this.direction = direction;
        return this;
    }

    public String getAccountId() {
        return accountId;
    }

    public PostStopOrderRequest setAccountId(String accountId) {
        this.accountId = accountId;
        return this;
    }

    public StopOrderExpirationType getExpirationType() {
        return expirationType;
    }

    public PostStopOrderRequest setExpirationType(StopOrderExpirationType expirationType) {
        this.expirationType = expirationType;
        return this;
    }

    public StopOrderType getStopOrderType() {
        return stopOrderType;
    }

    public PostStopOrderRequest setStopOrderType(StopOrderType stopOrderType) {
        this.stopOrderType = stopOrderType;
        return this;
    }

    public Instant getExpireDate() {
        return expireDate;
    }

    public PostStopOrderRequest setExpireDate(Instant expireDate) {
        this.expireDate = expireDate;
        return this;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public PostStopOrderRequest setInstrumentId(String instrumentId) {
        this.instrumentId = instrumentId;
        return this;
    }

    public ExchangeOrderType getExchangeOrderType() {
        return exchangeOrderType;
    }

    public PostStopOrderRequest setExchangeOrderType(ExchangeOrderType exchangeOrderType) {
        this.exchangeOrderType = exchangeOrderType;
        return this;
    }

    public TakeProfitType getTakeProfitType() {
        return takeProfitType;
    }

    public PostStopOrderRequest setTakeProfitType(TakeProfitType takeProfitType) {
        this.takeProfitType = takeProfitType;
        return this;
    }

    public TrailingData getTrailingData() {
        return trailingData;
    }

    public PostStopOrderRequest setTrailingData(TrailingData trailingData) {
        this.trailingData = trailingData;
        return this;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public PostStopOrderRequest setPriceType(PriceType priceType) {
        this.priceType = priceType;
        return this;
    }

    public String getOrderId() {
        return orderId;
    }

    public PostStopOrderRequest setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }
}
