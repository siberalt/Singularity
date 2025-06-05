package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.user.GetAccountsRequest;
import com.siberalt.singularity.broker.contract.service.user.GetAccountsResponse;
import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.AccountTranslator;
import ru.tinkoff.piapi.core.UsersService;

public class UserService implements com.siberalt.singularity.broker.contract.service.user.UserService {

    protected UsersService usersService;

    public UserService(UsersService usersService) {
        this.usersService = usersService;
    }

    @Override
    public GetAccountsResponse getAccounts(GetAccountsRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() -> usersService.getAccountsSync());

        return new GetAccountsResponse()
                .setAccounts(ListTranslator.translate(response, AccountTranslator::toContract));
    }
}
