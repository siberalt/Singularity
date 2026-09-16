package com.siberalt.singularity.broker.contract.service.event.dispatcher.events;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.event.Event;

import java.util.Objects;
import java.util.UUID;

/**
 * A candle has closed on an instrument somebody subscribed to at a broker.
 * <p>
 * The event carries two identities, and they are not the same thing twice. {@link #getInstrumentUid()}
 * is what the broker calls the instrument - what the subscription was made for and what an order
 * placed in reaction has to name. The candle carries our own instrument id, which is what candle
 * history is stored and read by. A strategy needs both: it matches the event by the first and
 * reaches back into history by the second.
 */
public class NewCandleEvent extends Event {
    private final String instrumentUid;
    private final Candle candle;

    public NewCandleEvent(UUID id, String instrumentUid, Candle candle) {
        super(id);
        this.instrumentUid = instrumentUid;
        this.candle = candle;
    }

    public NewCandleEvent(String instrumentUid, Candle candle) {
        this(UUID.randomUUID(), instrumentUid, candle);
    }

    /** What the broker calls the instrument the candle is of. */
    public String getInstrumentUid() {
        return instrumentUid;
    }

    public Candle getCandle() {
        return candle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NewCandleEvent that)) return false;
        return Objects.equals(instrumentUid, that.instrumentUid) && candle.equals(that.candle);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(instrumentUid) + candle.hashCode();
    }
}
