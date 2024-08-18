package investtech.broker.impl.tinkoff.translation;

import investtech.broker.contract.service.user.Account;

public class AccountTranslator {
    public static ru.tinkoff.piapi.contract.v1.Account toTinkoff(Account account) {
        return ru.tinkoff.piapi.contract.v1.Account.newBuilder()
                .setId(account.getId())
                .setType(AccountTypeTranslator.toTinkoff(account.getType()))
                .setName(account.getName())
                .setStatus(AccountStatusTranslator.toTinkoff(account.getStatus()))
                .setOpenedDate(TimestampTranslator.toTinkoff(account.getOpenedDate()))
                .setClosedDate(TimestampTranslator.toTinkoff(account.getClosedDate()))
                .setAccessLevel(AccessLevelTranslator.toTinkoff(account.getAccessLevel()))
                .build();
    }

    public static Account toContract(ru.tinkoff.piapi.contract.v1.Account account) {
        return new Account()
                .setId(account.getId())
                .setType(AccountTypeTranslator.toContract(account.getType()))
                .setName(account.getName())
                .setStatus(AccountStatusTranslator.toContract(account.getStatus()))
                .setOpenedDate(TimestampTranslator.toContract(account.getOpenedDate()))
                .setClosedDate(TimestampTranslator.toContract(account.getClosedDate()))
                .setAccessLevel(AccessLevelTranslator.toContract(account.getAccessLevel()));
    }
}
