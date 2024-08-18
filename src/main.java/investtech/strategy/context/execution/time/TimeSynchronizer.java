package investtech.strategy.context.execution.time;

import investtech.strategy.context.TimeSynchronizerInterface;

import java.time.Instant;

public class TimeSynchronizer implements TimeSynchronizerInterface {
    @Override
    public Instant currentTime() {
        return Instant.now();
    }
}
