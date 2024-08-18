package investtech.broker.contract.service.order.response;

import investtech.broker.contract.value.money.Money;
import java.time.Instant;

public class OrderStage {
    protected Money price;
    protected long quantity;
    protected String tradeId;
    protected Instant executionTime;

    public Money getPrice() {
        return price;
    }

    public OrderStage setPrice(Money price) {
        this.price = price;
        return this;
    }

    public long getQuantity() {
        return quantity;
    }

    public OrderStage setQuantity(long quantity) {
        this.quantity = quantity;
        return this;
    }

    public String getTradeId() {
        return tradeId;
    }

    public OrderStage setTradeId(String tradeId) {
        this.tradeId = tradeId;
        return this;
    }

    public Instant getExecutionTime() {
        return executionTime;
    }

    public OrderStage setExecutionTime(Instant executionTime) {
        this.executionTime = executionTime;
        return this;
    }
}
