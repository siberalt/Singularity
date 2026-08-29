package com.siberalt.singularity.event.handler;

import com.siberalt.singularity.broker.contract.service.event.dispatcher.events.NewCandleEvent;
import com.siberalt.singularity.entity.candle.WriteCandleRepository;
import com.siberalt.singularity.event.EventHandler;
import com.siberalt.singularity.event.subscription.Subscription;

public class CandleSaveEventHandler implements EventHandler<NewCandleEvent> {
    private final WriteCandleRepository writeCandleRepository;

    public CandleSaveEventHandler(WriteCandleRepository writeCandleRepository) {
        this.writeCandleRepository = writeCandleRepository;
    }

    @Override
    public void handle(NewCandleEvent event, Subscription subscription) {
        writeCandleRepository.save(event.getCandle());
    }
}
