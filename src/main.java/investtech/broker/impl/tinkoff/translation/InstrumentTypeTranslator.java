package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.instrument.common.InstrumentType;

public class InstrumentTypeTranslator {
    public static ru.tinkoff.piapi.contract.v1.InstrumentType toTinkoff(InstrumentType instrumentType) {
        return switch (instrumentType) {
            case UNSPECIFIED, CRYPTO -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_UNSPECIFIED;
            case BOND -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_BOND;
            case SHARE -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_SHARE;
            case CURRENCY -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_CURRENCY;
            case ETF -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_ETF;
            case FUTURES -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_FUTURES;
            case SP -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_SP;
            case OPTION -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_OPTION;
            case CLEARING_CERTIFICATE ->
                    ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_CLEARING_CERTIFICATE;
            case INDEX -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_INDEX;
            case COMMODITY -> ru.tinkoff.piapi.contract.v1.InstrumentType.INSTRUMENT_TYPE_COMMODITY;
        };
    }

    public static InstrumentType toContract(ru.tinkoff.piapi.contract.v1.InstrumentType instrumentType) {
        return switch (instrumentType) {
            case INSTRUMENT_TYPE_UNSPECIFIED, UNRECOGNIZED -> null;
            case INSTRUMENT_TYPE_BOND -> InstrumentType.BOND;
            case INSTRUMENT_TYPE_SHARE -> InstrumentType.SHARE;
            case INSTRUMENT_TYPE_CURRENCY -> InstrumentType.CURRENCY;
            case INSTRUMENT_TYPE_ETF -> InstrumentType.ETF;
            case INSTRUMENT_TYPE_FUTURES -> InstrumentType.FUTURES;
            case INSTRUMENT_TYPE_SP -> InstrumentType.SP;
            case INSTRUMENT_TYPE_OPTION -> InstrumentType.OPTION;
            case INSTRUMENT_TYPE_CLEARING_CERTIFICATE -> InstrumentType.CLEARING_CERTIFICATE;
            case INSTRUMENT_TYPE_INDEX -> InstrumentType.INDEX;
            case INSTRUMENT_TYPE_COMMODITY -> InstrumentType.COMMODITY;
        };
    }
}
