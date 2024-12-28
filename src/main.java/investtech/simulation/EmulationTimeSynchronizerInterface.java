package investtech.simulation;

import investtech.strategy.context.TimeSynchronizerInterface;

import java.time.Instant;

public interface EmulationTimeSynchronizerInterface extends TimeSynchronizerInterface {
    void syncCurrentTime(Instant currentTime);
}
