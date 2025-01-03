package investtech.broker.impl.mock.shared.order;

import investtech.broker.contract.service.instrument.Instrument;
import investtech.broker.contract.service.order.request.PostOrderRequest;
import investtech.broker.contract.service.order.response.ExecutionStatus;
import investtech.broker.contract.service.order.response.OrderState;
import investtech.broker.contract.value.money.Money;
import investtech.broker.contract.value.quotation.Quotation;

import java.time.Instant;
import java.util.UUID;

public class Order {
    protected Quotation instrumentPrice;
    protected PostOrderRequest request;
    protected Instant createdDate;
    protected String requestId;
    protected ExecutionStatus executionStatus;
    protected Instrument instrument;
    protected Quotation initialPrice;
    protected Quotation totalPrice;
    protected Quotation commissionPrice;
    protected Quotation totalPricePerOne;
    protected Quotation initialPricePerOne;
    protected long lotsExecuted;
    protected long lotsRequested;

    public Quotation getTotalPricePerOne() {
        return totalPricePerOne;
    }

    public Order setTotalPricePerOne(Quotation totalPricePerOne) {
        this.totalPricePerOne = totalPricePerOne;
        return this;
    }

    public Quotation getInitialPrice() {
        return initialPrice;
    }

    public Order setInitialPrice(Quotation initialPrice) {
        this.initialPrice = initialPrice;
        return this;
    }

    public Quotation getTotalPrice() {
        return totalPrice;
    }

    public Order setTotalPrice(Quotation totalPrice) {
        this.totalPrice = totalPrice;
        return this;
    }

    public Quotation getCommissionPrice() {
        return commissionPrice;
    }

    public Order setCommissionPrice(Quotation commissionPrice) {
        this.commissionPrice = commissionPrice;
        return this;
    }

    public Order setInstrumentPrice(Quotation instrumentPrice) {
        this.instrumentPrice = instrumentPrice;
        return this;
    }

    public Quotation getInstrumentPrice() {
        return instrumentPrice;
    }

    public PostOrderRequest getRequest() {
        return request;
    }

    public Order setRequest(PostOrderRequest request) {
        this.request = request;
        return this;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public Order setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
        return this;
    }

    public String getRequestId() {
        return requestId;
    }

    public Order setRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    public ExecutionStatus getExecutionStatus() {
        return executionStatus;
    }

    public Quotation getInitialPricePerOne() {
        return initialPricePerOne;
    }

    public Order setInitialPricePerOne(Quotation initialPricePerOne) {
        this.initialPricePerOne = initialPricePerOne;
        return this;
    }

    public Order setExecutionStatus(ExecutionStatus executionStatus) {
        this.executionStatus = executionStatus;
        return this;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public Order setInstrument(Instrument instrument) {
        this.instrument = instrument;
        return this;
    }

    public long getLotsExecuted() {
        return lotsExecuted;
    }

    public Order setLotsExecuted(long lotsExecuted) {
        this.lotsExecuted = lotsExecuted;
        return this;
    }

    public long getLotsRequested() {
        return lotsRequested;
    }

    public Order setLotsRequested(long lotsRequested) {
        this.lotsRequested = lotsRequested;
        return this;
    }

    public OrderState getState() {
        String instrumentCurrency = getInstrument().getCurrency();

        return new OrderState()
            .setOrderId(request.getOrderId())
            .setDirection(request.getDirection())
            .setOrderType(request.getOrderType())
            .setInstrumentUid(request.getInstrumentId())
            .setOrderDate(createdDate)
            .setOrderRequestId(requestId)
            .setLotsRequested(request.getQuantity())
            .setLotsExecuted(lotsExecuted)
            .setInitialOrderPrice(Money.of(instrumentCurrency, initialPrice))
            .setExecutedOrderPrice(Money.of(instrumentCurrency, totalPricePerOne))
            .setExecutedCommission(Money.of(instrumentCurrency, commissionPrice))
            .setTotalOrderAmount(Money.of(instrumentCurrency, totalPrice))
            .setInitialSecurityPrice(Money.of(instrumentCurrency, initialPricePerOne))
            .setExecutionStatus(executionStatus)
            .setCurrency(instrumentCurrency);
    }

    public static Order of(
        PostOrderRequest request,
        Instant date,
        ExecutionStatus status
    ) {
        return of(request, date, UUID.randomUUID().toString(), status);
    }

    public static Order of(
        PostOrderRequest request,
        Instant date,
        String requestId,
        ExecutionStatus status
    ) {
        return new Order()
            .setRequest(request)
            .setCreatedDate(date)
            .setRequestId(requestId)
            .setExecutionStatus(status);
    }
}
