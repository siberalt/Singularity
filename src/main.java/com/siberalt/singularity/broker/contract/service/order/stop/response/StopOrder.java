package com.siberalt.singularity.broker.contract.service.order.stop.response;

import com.siberalt.singularity.broker.contract.service.order.common.ExchangeOrderType;
import com.siberalt.singularity.broker.contract.service.order.stop.common.StopOrderStatusOption;
import com.siberalt.singularity.broker.contract.service.order.stop.request.StopOrderDirection;
import com.siberalt.singularity.broker.contract.service.order.stop.request.StopOrderType;
import com.siberalt.singularity.broker.contract.service.order.stop.request.TakeProfitType;
import com.siberalt.singularity.broker.contract.service.order.stop.request.TrailingValueType;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;

public class StopOrder {
    public static class TrailingData {
        protected Quotation indent;
        protected TrailingValueType indentType;
        protected Quotation spread;
        protected TrailingValueType spreadType;
        protected TrailingStopStatus status;
        protected Quotation price;
        protected Quotation extr;

        public Quotation getIndent() {
            return indent;
        }

        public TrailingData setIndent(Quotation indent) {
            this.indent = indent;
            return this;
        }

        public TrailingValueType getIndentType() {
            return indentType;
        }

        public TrailingData setIndentType(TrailingValueType indentType) {
            this.indentType = indentType;
            return this;
        }

        public Quotation getSpread() {
            return spread;
        }

        public TrailingData setSpread(Quotation spread) {
            this.spread = spread;
            return this;
        }

        public TrailingValueType getSpreadType() {
            return spreadType;
        }

        public TrailingData setSpreadType(TrailingValueType spreadType) {
            this.spreadType = spreadType;
            return this;
        }

        public TrailingStopStatus getStatus() {
            return status;
        }

        public TrailingData setStatus(TrailingStopStatus status) {
            this.status = status;
            return this;
        }

        public Quotation getPrice() {
            return price;
        }

        public TrailingData setPrice(Quotation price) {
            this.price = price;
            return this;
        }

        public Quotation getExtr() {
            return extr;
        }

        public TrailingData setExtr(Quotation extr) {
            this.extr = extr;
            return this;
        }
    }

    protected String stopOrderId;
    protected long lotsRequested;
    protected StopOrderDirection direction;
    protected String currency;
    protected StopOrderType orderType;
    protected Instant createDate;
    protected Instant activationDateTime;
    protected Instant expirationTime;
    protected Money price;
    protected Money stopPrice;
    protected String instrumentUid;
    protected TakeProfitType takeProfitType;
    protected StopOrder.TrailingData trailingData;
    protected StopOrderStatusOption status;
    protected ExchangeOrderType exchangeOrderType;

    public String getStopOrderId() {
        return stopOrderId;
    }

    public StopOrder setStopOrderId(String stopOrderId) {
        this.stopOrderId = stopOrderId;
        return this;
    }

    public long getLotsRequested() {
        return lotsRequested;
    }

    public StopOrder setLotsRequested(long lotsRequested) {
        this.lotsRequested = lotsRequested;
        return this;
    }

    public StopOrderDirection getDirection() {
        return direction;
    }

    public StopOrder setDirection(StopOrderDirection direction) {
        this.direction = direction;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public StopOrder setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public StopOrderType getOrderType() {
        return orderType;
    }

    public StopOrder setOrderType(StopOrderType orderType) {
        this.orderType = orderType;
        return this;
    }

    public Instant getCreateDate() {
        return createDate;
    }

    public StopOrder setCreateDate(Instant createDate) {
        this.createDate = createDate;
        return this;
    }

    public Instant getActivationDateTime() {
        return activationDateTime;
    }

    public StopOrder setActivationDateTime(Instant activationDateTime) {
        this.activationDateTime = activationDateTime;
        return this;
    }

    public Instant getExpirationTime() {
        return expirationTime;
    }

    public StopOrder setExpirationTime(Instant expirationTime) {
        this.expirationTime = expirationTime;
        return this;
    }

    public Money getPrice() {
        return price;
    }

    public StopOrder setPrice(Money price) {
        this.price = price;
        return this;
    }

    public Money getStopPrice() {
        return stopPrice;
    }

    public StopOrder setStopPrice(Money stopPrice) {
        this.stopPrice = stopPrice;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public StopOrder setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public TakeProfitType getTakeProfitType() {
        return takeProfitType;
    }

    public StopOrder setTakeProfitType(TakeProfitType takeProfitType) {
        this.takeProfitType = takeProfitType;
        return this;
    }

    public TrailingData getTrailingData() {
        return trailingData;
    }

    public StopOrder setTrailingData(TrailingData trailingData) {
        this.trailingData = trailingData;
        return this;
    }

    public StopOrderStatusOption getStatus() {
        return status;
    }

    public StopOrder setStatus(StopOrderStatusOption status) {
        this.status = status;
        return this;
    }

    public ExchangeOrderType getExchangeOrderType() {
        return exchangeOrderType;
    }

    public StopOrder setExchangeOrderType(ExchangeOrderType exchangeOrderType) {
        this.exchangeOrderType = exchangeOrderType;
        return this;
    }
}
