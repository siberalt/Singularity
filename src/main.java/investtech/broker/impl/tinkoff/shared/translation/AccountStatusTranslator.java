package investtech.broker.impl.tinkoff.shared.translation;

import investtech.broker.contract.service.user.AccountStatus;

public class AccountStatusTranslator {
    public static ru.tinkoff.piapi.contract.v1.AccountStatus toTinkoff(AccountStatus accountStatus) {
        return switch (accountStatus) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.AccountStatus.ACCOUNT_STATUS_UNSPECIFIED;
            case NEW -> ru.tinkoff.piapi.contract.v1.AccountStatus.ACCOUNT_STATUS_NEW;
            case OPEN -> ru.tinkoff.piapi.contract.v1.AccountStatus.ACCOUNT_STATUS_OPEN;
            case CLOSED -> ru.tinkoff.piapi.contract.v1.AccountStatus.ACCOUNT_STATUS_CLOSED;
        };
    }

    public static AccountStatus toContract(ru.tinkoff.piapi.contract.v1.AccountStatus accountStatus) {
        return switch (accountStatus) {
            case ACCOUNT_STATUS_UNSPECIFIED -> AccountStatus.UNSPECIFIED;
            case ACCOUNT_STATUS_NEW -> AccountStatus.NEW;
            case ACCOUNT_STATUS_OPEN -> AccountStatus.OPEN;
            case ACCOUNT_STATUS_CLOSED -> AccountStatus.CLOSED;
            case UNRECOGNIZED -> null;
        };
    }
}
