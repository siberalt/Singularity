package investtech.emulation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TimeFlowEmulator {
    protected EventObserver eventObserver;

    protected List<TimeDependentUnitInterface> timeDependentUnits = new ArrayList<>();

    protected EmulationTimeSynchronizerInterface timeSynchronizer;

    public TimeFlowEmulator(EventObserver eventObserver, EmulationTimeSynchronizerInterface timeSynchronizer) {
        this.eventObserver = eventObserver;
        this.timeSynchronizer = timeSynchronizer;
    }

    public TimeFlowEmulator addTimeDependentUnit(TimeDependentUnitInterface timeDependentUnit) {
        this.timeDependentUnits.add(timeDependentUnit);

        return this;
    }

    public void run(Instant from, Instant to) {
        var currentTime = from;

        while (currentTime.isBefore(to)) {
            timeSynchronizer.syncCurrentTime(currentTime);

            for (var timeDependentUnit : timeDependentUnits) {
                timeDependentUnit.tick();
            }

            if (!eventObserver.hasComingEvents()) {
                break;
            }

            currentTime = eventObserver.getComingEventsTimePoint();
            eventObserver.rewindToNextEvents();
        }
    }
}
