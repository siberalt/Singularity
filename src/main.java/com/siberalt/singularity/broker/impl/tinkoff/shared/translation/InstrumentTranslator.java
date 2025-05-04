package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.entity.instrument.Instrument;

public class InstrumentTranslator {
    public static ru.tinkoff.piapi.contract.v1.InstrumentShort toTinkoff(Instrument instrument) {
        return ru.tinkoff.piapi.contract.v1.InstrumentShort.newBuilder()
                .setName(instrument.getName())
                .setUid(instrument.getUid())
                .setPositionUid(instrument.getPositionUid())
                .setInstrumentKind(InstrumentTypeTranslator.toTinkoff(instrument.getInstrumentType()))
                .build();
    }

    public static Instrument toContract(ru.tinkoff.piapi.contract.v1.InstrumentShort instrument) {
        return new Instrument()
                .setName(instrument.getName())
                .setUid(instrument.getUid())
                .setPositionUid(instrument.getPositionUid())
                .setInstrumentType(InstrumentTypeTranslator.toContract(instrument.getInstrumentKind()));
    }
}
