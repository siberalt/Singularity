package investtech.broker.contract.service.order.response;

import investtech.broker.contract.service.order.request.OrderDirection;
import investtech.broker.contract.service.order.request.OrderType;
import investtech.broker.contract.value.money.Money;

public class PostOrderResponse {
    protected String orderId;
    protected ExecutionStatus executionReportStatus;
    protected long lotsRequested;
    protected long lotsExecuted;
    protected Money initialOrderPrice;
    protected Money executedOrderPrice;
    protected Money totalOrderAmount;
    protected Money initialCommission;
    protected Money executedCommission;
    protected Money aciValue;
    protected OrderDirection direction;
    protected Money initialSecurityPrice;
    protected OrderType orderType;
    protected String message;
    protected String instrumentUid;
    protected String orderRequestId;

    public String getOrderId() {
        return orderId;
    }

    public PostOrderResponse setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionReportStatus;
    }

    public PostOrderResponse setExecutionStatus(ExecutionStatus executionReportStatus) {
        this.executionReportStatus = executionReportStatus;
        return this;
    }

    public long getLotsRequested() {
        return lotsRequested;
    }

    public PostOrderResponse setLotsRequested(long lotsRequested) {
        this.lotsRequested = lotsRequested;
        return this;
    }

    public long getLotsExecuted() {
        return lotsExecuted;
    }

    public PostOrderResponse setLotsExecuted(long lotsExecuted) {
        this.lotsExecuted = lotsExecuted;
        return this;
    }

    public Money getInitialOrderPrice() {
        return initialOrderPrice;
    }

    public PostOrderResponse setInitialOrderPrice(Money initialOrderPrice) {
        this.initialOrderPrice = initialOrderPrice;
        return this;
    }

    public Money getExecutedOrderPrice() {
        return executedOrderPrice;
    }

    public PostOrderResponse setExecutedOrderPrice(Money executedOrderPrice) {
        this.executedOrderPrice = executedOrderPrice;
        return this;
    }

    public Money getTotalOrderAmount() {
        return totalOrderAmount;
    }

    public PostOrderResponse setTotalOrderAmount(Money totalOrderAmount) {
        this.totalOrderAmount = totalOrderAmount;
        return this;
    }

    public Money getInitialCommission() {
        return initialCommission;
    }

    public PostOrderResponse setInitialCommission(Money initialCommission) {
        this.initialCommission = initialCommission;
        return this;
    }

    public Money getExecutedCommission() {
        return executedCommission;
    }

    public PostOrderResponse setExecutedCommission(Money executedCommission) {
        this.executedCommission = executedCommission;
        return this;
    }

    public Money getAciValue() {
        return aciValue;
    }

    public PostOrderResponse setAciValue(Money aciValue) {
        this.aciValue = aciValue;
        return this;
    }

    public OrderDirection getDirection() {
        return direction;
    }

    public PostOrderResponse setDirection(OrderDirection direction) {
        this.direction = direction;
        return this;
    }

    public Money getInitialSecurityPrice() {
        return initialSecurityPrice;
    }

    public PostOrderResponse setInitialSecurityPrice(Money initialSecurityPrice) {
        this.initialSecurityPrice = initialSecurityPrice;
        return this;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public PostOrderResponse setOrderType(OrderType orderType) {
        this.orderType = orderType;
        return this;
    }

    public String getMessage() {
        return message;
    }

    public PostOrderResponse setMessage(String message) {
        this.message = message;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public PostOrderResponse setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public String getOrderRequestId() {
        return orderRequestId;
    }

    public PostOrderResponse setOrderRequestId(String orderRequestId) {
        this.orderRequestId = orderRequestId;
        return this;
    }
}
