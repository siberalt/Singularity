package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.operation.response.PositionSecurities;

public class PositionSecuritiesTranslator {
    public static ru.tinkoff.piapi.contract.v1.PositionsSecurities toTinkoff(PositionSecurities positionsSecurities) {
        return ru.tinkoff.piapi.contract.v1.PositionsSecurities.newBuilder()
                .setBlocked(positionsSecurities.getBlocked())
                .setBalance(positionsSecurities.getBalance())
                .setPositionUid(positionsSecurities.getPositionUid())
                .setInstrumentUid(positionsSecurities.getInstrumentUid())
                .setExchangeBlocked(positionsSecurities.isExchangeBlocked())
                .setInstrumentType(positionsSecurities.getInstrumentType())
                .build();
    }

    public static PositionSecurities toContract(ru.tinkoff.piapi.contract.v1.PositionsSecurities positionsSecurities) {
        return new PositionSecurities()
                .setBlocked(positionsSecurities.getBlocked())
                .setBalance(positionsSecurities.getBalance())
                .setPositionUid(positionsSecurities.getPositionUid())
                .setInstrumentUid(positionsSecurities.getInstrumentUid())
                .setExchangeBlocked(positionsSecurities.getExchangeBlocked())
                .setInstrumentType(positionsSecurities.getInstrumentType());

    }
}
