package investtech.strategy.context.emulation.time;

import investtech.simulation.EmulationTimeSynchronizerInterface;

import java.time.Instant;

public class SimulationTimeSynchronizer implements EmulationTimeSynchronizerInterface {
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
