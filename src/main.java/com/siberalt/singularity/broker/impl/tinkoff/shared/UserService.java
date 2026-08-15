package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.user.GetAccountsRequest;
import com.siberalt.singularity.broker.contract.service.user.GetAccountsResponse;
import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.AccountTranslator;
import ru.tinkoff.piapi.contract.v1.UsersServiceGrpc;

public class UserService implements com.siberalt.singularity.broker.contract.service.user.UserService {

    protected UsersServiceGrpc.UsersServiceBlockingStub usersService;

    public UserService(
        UsersServiceGrpc.UsersServiceBlockingStub usersService) {
        this.usersService = usersService;
    }

    @Override
    public GetAccountsResponse getAccounts(GetAccountsRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() ->
            usersService.getAccounts(ru.tinkoff.piapi.contract.v1.GetAccountsRequest.newBuilder().build())
        );

        return new GetAccountsResponse()
            .setAccounts(ListTranslator.translate(response.getAccountsList(), AccountTranslator::toContract));
    }
}
