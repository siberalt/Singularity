package investtech.broker.contract.service.user;

import investtech.broker.contract.service.exception.AbstractException;

public interface UserServiceInterface {
    GetAccountsResponse getAccounts(GetAccountsRequest request) throws AbstractException;
}
