package com.siberalt.singularity.broker.contract.service.event.dispatcher.events;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.event.Event;

import java.util.UUID;

public class NewCandleEvent extends Event {
    private final Candle candle;

    public NewCandleEvent(UUID id, Candle candle) {
        super(id);
        this.candle = candle;
    }

    public NewCandleEvent(Candle candle) {
        super(UUID.randomUUID());
        this.candle = candle;
    }

    public Candle getCandle() {
        return candle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NewCandleEvent that)) return false;
        return candle.equals(that.candle);
    }

    @Override
    public int hashCode() {
        return candle.hashCode();
    }
}
