package com.siberalt.singularity.broker.contract.service.order.stop.response;
import java.time.Instant;

public class CancelStopOrderResponse {
    protected Instant time;

    public Instant getTime() {
        return time;
    }

    public CancelStopOrderResponse setTime(Instant time) {
        this.time = time;
        return this;
    }
}
