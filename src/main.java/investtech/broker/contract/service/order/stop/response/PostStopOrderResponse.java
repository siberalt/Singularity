package investtech.broker.contract.service.order.stop.response;

public class PostStopOrderResponse {
    protected String stopOrderId;

    public String getStopOrderId() {
        return stopOrderId;
    }

    public PostStopOrderResponse setStopOrderId(String stopOrderId) {
        this.stopOrderId = stopOrderId;
        return this;
    }
}
