package investtech.broker.contract.service.order.response;

import investtech.broker.contract.service.order.request.OrderDirection;
import investtech.broker.contract.service.order.request.OrderType;
import investtech.broker.contract.value.money.Money;

import java.time.Instant;
import java.util.List;

public class OrderState {
    protected String orderId;
    protected OrderExecutionReportStatus executionReportStatus;
    protected long lotsRequested;
    protected long lotsExecuted;
    protected Money initialOrderPrice;
    protected Money executedOrderPrice;
    protected Money totalOrderAmount;
    protected Money averagePositionPrice;
    protected Money initialCommission;
    protected Money executedCommission;
    protected OrderDirection direction;
    protected Money initialSecurityPrice;
    protected List<OrderStage> stages;
    protected Money serviceCommission;
    protected String currency;
    protected OrderType orderType;
    protected Instant orderDate;
    protected String instrumentUid;
    protected String orderRequestId;

    public String getOrderId() {
        return orderId;
    }

    public OrderState setOrderId(String orderId) {
        this.orderId = orderId;
        return this;
    }

    public OrderExecutionReportStatus getExecutionReportStatus() {
        return executionReportStatus;
    }

    public OrderState setExecutionReportStatus(OrderExecutionReportStatus executionReportStatus) {
        this.executionReportStatus = executionReportStatus;
        return this;
    }

    public long getLotsRequested() {
        return lotsRequested;
    }

    public OrderState setLotsRequested(long lotsRequested) {
        this.lotsRequested = lotsRequested;
        return this;
    }

    public long getLotsExecuted() {
        return lotsExecuted;
    }

    public OrderState setLotsExecuted(long lotsExecuted) {
        this.lotsExecuted = lotsExecuted;
        return this;
    }

    public Money getInitialOrderPrice() {
        return initialOrderPrice;
    }

    public OrderState setInitialOrderPrice(Money initialOrderPrice) {
        this.initialOrderPrice = initialOrderPrice;
        return this;
    }

    public Money getExecutedOrderPrice() {
        return executedOrderPrice;
    }

    public OrderState setExecutedOrderPrice(Money executedOrderPrice) {
        this.executedOrderPrice = executedOrderPrice;
        return this;
    }

    public Money getTotalOrderAmount() {
        return totalOrderAmount;
    }

    public OrderState setTotalOrderAmount(Money totalOrderAmount) {
        this.totalOrderAmount = totalOrderAmount;
        return this;
    }

    public Money getAveragePositionPrice() {
        return averagePositionPrice;
    }

    public OrderState setAveragePositionPrice(Money averagePositionPrice) {
        this.averagePositionPrice = averagePositionPrice;
        return this;
    }

    public Money getInitialCommission() {
        return initialCommission;
    }

    public OrderState setInitialCommission(Money initialCommission) {
        this.initialCommission = initialCommission;
        return this;
    }

    public Money getExecutedCommission() {
        return executedCommission;
    }

    public OrderState setExecutedCommission(Money executedCommission) {
        this.executedCommission = executedCommission;
        return this;
    }

    public OrderDirection getDirection() {
        return direction;
    }

    public OrderState setDirection(OrderDirection direction) {
        this.direction = direction;
        return this;
    }

    public Money getInitialSecurityPrice() {
        return initialSecurityPrice;
    }

    public OrderState setInitialSecurityPrice(Money initialSecurityPrice) {
        this.initialSecurityPrice = initialSecurityPrice;
        return this;
    }

    public List<OrderStage> getStages() {
        return stages;
    }

    public OrderState setStages(List<OrderStage> stages) {
        this.stages = stages;
        return this;
    }

    public Money getServiceCommission() {
        return serviceCommission;
    }

    public OrderState setServiceCommission(Money serviceCommission) {
        this.serviceCommission = serviceCommission;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public OrderState setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public OrderState setOrderType(OrderType orderType) {
        this.orderType = orderType;
        return this;
    }

    public Instant getOrderDate() {
        return orderDate;
    }

    public OrderState setOrderDate(Instant orderDate) {
        this.orderDate = orderDate;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public OrderState setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public String getOrderRequestId() {
        return orderRequestId;
    }

    public OrderState setOrderRequestId(String orderRequestId) {
        this.orderRequestId = orderRequestId;
        return this;
    }

    @Override
    public String toString() {
        var elements = List.of(
                String.format("orderId: %s", orderId),
                String.format("executionReportStatus: %s", executionReportStatus),
                String.format("lotsRequested: %d", lotsRequested),
                String.format("lotsExecuted: %d", lotsExecuted),
                String.format("initialOrderPrice: %s", initialOrderPrice),
                String.format("executedOrderPrice: %s", executedOrderPrice),
                String.format("totalOrderAmount: %s", totalOrderAmount),
                String.format("averagePositionPrice: %s", averagePositionPrice),
                String.format("initialCommission: %s", initialCommission),
                String.format("executedCommission: %s", executedCommission),
                String.format("direction: %s", direction),
                String.format("initialSecurityPrice: %s", initialSecurityPrice),
                String.format("stages: %s", stages),
                String.format("serviceCommission: %s", serviceCommission),
                String.format("currency: %s", currency),
                String.format("orderType: %s", orderType),
                String.format("orderDate: %s", orderDate),
                String.format("instrumentUid: %s", instrumentUid),
                String.format("orderRequestId: %s", orderRequestId)
        );

        return String.join("\n", elements);
    }
}
