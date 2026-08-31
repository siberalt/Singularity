package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.entity.instrument.Instrument;
import ru.tinkoff.piapi.contract.v1.Share;

public class ShareTranslator {
    public static Instrument toContract(Share share) {
        return new Instrument()
                .setUid(share.getUid())
                .setName(share.getName())
                .setIsin(share.getIsin())
                .setLot(share.getLot())
                .setCurrency(share.getCurrency())
                .setInstrumentType(InstrumentType.SHARE);
    }
}
