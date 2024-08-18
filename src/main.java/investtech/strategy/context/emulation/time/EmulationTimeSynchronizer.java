package investtech.strategy.context.emulation.time;

import investtech.emulation.EmulationTimeSynchronizerInterface;
import investtech.emulation.TimeDependentUnitInterface;
import investtech.strategy.context.TimeSynchronizerInterface;

import java.time.Instant;

public class EmulationTimeSynchronizer implements EmulationTimeSynchronizerInterface {
    protected Instant currentTime;

    @Override
    public Instant currentTime() {
        return currentTime;
    }

    @Override
    public void syncCurrentTime(Instant currentTime) {
        this.currentTime = currentTime;
    }
}
