package investtech.broker.impl.tinkoff;

import investtech.broker.common.ListTranslator;
import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.user.GetAccountsRequest;
import investtech.broker.contract.service.user.GetAccountsResponse;
import investtech.broker.contract.service.user.UserServiceInterface;
import investtech.broker.impl.tinkoff.exception.ExceptionConverter;
import investtech.broker.impl.tinkoff.translation.AccountTranslator;
import ru.tinkoff.piapi.core.UsersService;

public class UserService implements UserServiceInterface {

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
