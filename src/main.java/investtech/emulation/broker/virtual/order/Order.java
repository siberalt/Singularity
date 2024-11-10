package investtech.emulation.broker.virtual.order;

import investtech.broker.contract.service.instrument.Instrument;
import investtech.broker.contract.service.order.request.PostOrderRequest;
import investtech.broker.contract.service.order.response.OrderExecutionReportStatus;
import investtech.broker.contract.service.order.response.OrderState;
import investtech.broker.contract.value.quatation.Quotation;
import investtech.emulation.Event;

import java.time.Instant;
import java.util.UUID;

public class Order {
    protected Quotation instrumentPrice;
    protected Event event;
    protected PostOrderRequest request;
    protected Instant createdDate;
    protected String requestId;
    protected OrderExecutionReportStatus reportStatus;
    protected Instrument instrument;
    protected Quotation initialPrice;
    protected Quotation totalPrice;
    protected Quotation commissionPrice;
    protected Quotation executedPrice;

    public Quotation getExecutedPrice() {
        return executedPrice;
    }

    public Order setExecutedPrice(Quotation executedPrice) {
        this.executedPrice = executedPrice;
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

    public Event getEvent() {
        return event;
    }

    public Order setEvent(Event event) {
        this.event = event;
        return this;
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

    public OrderExecutionReportStatus getReportStatus() {
        return reportStatus;
    }

    public Order setReportStatus(OrderExecutionReportStatus reportStatus) {
        this.reportStatus = reportStatus;
        return this;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public Order setInstrument(Instrument instrument) {
        this.instrument = instrument;
        return this;
    }

    public OrderState getState() {
        return new OrderState()
                .setOrderId(request.getOrderId())
                .setDirection(request.getDirection())
                .setOrderType(request.getOrderType())
                .setInstrumentUid(request.getInstrumentId())
                .setOrderDate(createdDate)
                .setOrderRequestId(requestId)
                .setLotsRequested(request.getQuantity());
    }

    public static Order of(
            Event event,
            PostOrderRequest request,
            Instant date,
            OrderExecutionReportStatus status
    ) {
        return of(event, request, date, UUID.randomUUID().toString(), status);
    }

    public static Order of(
            Event event,
            PostOrderRequest request,
            Instant date,
            String requestId,
            OrderExecutionReportStatus status
    ) {
        return new Order()
                .setEvent(event)
                .setRequest(request)
                .setCreatedDate(date)
                .setRequestId(requestId)
                .setReportStatus(status);
    }
}
