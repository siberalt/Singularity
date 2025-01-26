package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.operation.response.Position;

public class PositionSecuritiesTranslator {
    public static ru.tinkoff.piapi.contract.v1.PositionsSecurities toTinkoff(Position positionsSecurities) {
        return ru.tinkoff.piapi.contract.v1.PositionsSecurities.newBuilder()
                .setBlocked(positionsSecurities.getBlocked())
                .setBalance(positionsSecurities.getBalance())
                .setPositionUid(positionsSecurities.getPositionUid())
                .setInstrumentUid(positionsSecurities.getInstrumentUid())
                .setExchangeBlocked(positionsSecurities.isExchangeBlocked())
                .setInstrumentType(positionsSecurities.getInstrumentType())
                .build();
    }

    public static Position toContract(ru.tinkoff.piapi.contract.v1.PositionsSecurities positionsSecurities) {
        return new Position()
                .setBlocked(positionsSecurities.getBlocked())
                .setBalance(positionsSecurities.getBalance())
                .setPositionUid(positionsSecurities.getPositionUid())
                .setInstrumentUid(positionsSecurities.getInstrumentUid())
                .setExchangeBlocked(positionsSecurities.getExchangeBlocked())
                .setInstrumentType(positionsSecurities.getInstrumentType());

    }
}
