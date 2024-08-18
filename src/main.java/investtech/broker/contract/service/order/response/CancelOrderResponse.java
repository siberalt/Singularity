package investtech.broker.contract.service.order.response;

import java.time.Instant;

public class CancelOrderResponse {
    protected Instant time;

    public Instant getTime() {
        return time;
    }

    public CancelOrderResponse setTime(Instant time) {
        this.time = time;
        return this;
    }
}
