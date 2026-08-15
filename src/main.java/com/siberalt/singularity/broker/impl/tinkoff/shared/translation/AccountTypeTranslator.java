package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.siberalt.singularity.broker.contract.service.user.AccountType;

public class AccountTypeTranslator {
    public static ru.tinkoff.piapi.contract.v1.AccountType toTinkoff(AccountType accountType) {
        return switch (accountType) {
            case UNSPECIFIED -> ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_UNSPECIFIED;
            case ORDINARY -> ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_TINKOFF;
            case IIS -> ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_TINKOFF_IIS;
            case INVEST_BOX -> ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_INVEST_BOX;
            case INVEST_FUND -> ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_INVEST_FUND;
            case DEBIT -> ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_DEBIT;
            case DFA -> ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_DFA;
            case SAVING -> ru.tinkoff.piapi.contract.v1.AccountType.ACCOUNT_TYPE_SAVING;
        };
    }

    public static AccountType toContract(ru.tinkoff.piapi.contract.v1.AccountType accountType) {
        return switch (accountType) {
            case ACCOUNT_TYPE_UNSPECIFIED -> AccountType.UNSPECIFIED;
            case ACCOUNT_TYPE_TINKOFF -> AccountType.ORDINARY;
            case ACCOUNT_TYPE_TINKOFF_IIS -> AccountType.IIS;
            case ACCOUNT_TYPE_INVEST_BOX -> AccountType.INVEST_BOX;
            case ACCOUNT_TYPE_INVEST_FUND -> AccountType.INVEST_FUND;
            case ACCOUNT_TYPE_DEBIT -> AccountType.DEBIT;
            case ACCOUNT_TYPE_DFA -> AccountType.DFA;
            case ACCOUNT_TYPE_SAVING -> AccountType.SAVING;
            case UNRECOGNIZED -> null;
        };
    }
}
