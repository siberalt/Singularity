package investtech.emulation;

public interface EventInvokerInterface extends TimeDependentUnitInterface {
    void observeEventsBy(EventObserver observer);
}
