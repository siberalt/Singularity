package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.service.user.AccessLevel;

public class AccessLevelTranslator {
    public static ru.tinkoff.piapi.contract.v1.AccessLevel toTinkoff(AccessLevel accessLevel) {
        return switch (accessLevel) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.AccessLevel.ACCOUNT_ACCESS_LEVEL_UNSPECIFIED;
            case FULL_ACCESS -> ru.tinkoff.piapi.contract.v1.AccessLevel.ACCOUNT_ACCESS_LEVEL_FULL_ACCESS;
            case READ_ONLY -> ru.tinkoff.piapi.contract.v1.AccessLevel.ACCOUNT_ACCESS_LEVEL_READ_ONLY;
            case NO_ACCESS -> ru.tinkoff.piapi.contract.v1.AccessLevel.ACCOUNT_ACCESS_LEVEL_NO_ACCESS;
        };
    }

    public static AccessLevel toContract(ru.tinkoff.piapi.contract.v1.AccessLevel accessLevel) {
        return switch (accessLevel) {
            case ACCOUNT_ACCESS_LEVEL_UNSPECIFIED -> AccessLevel.UNSPECIFIED;
            case ACCOUNT_ACCESS_LEVEL_FULL_ACCESS -> AccessLevel.FULL_ACCESS;
            case ACCOUNT_ACCESS_LEVEL_READ_ONLY -> AccessLevel.READ_ONLY;
            case ACCOUNT_ACCESS_LEVEL_NO_ACCESS -> AccessLevel.NO_ACCESS;
            case UNRECOGNIZED -> null;
        };
    }
}
