package com.siberalt.singularity.broker.contract.service.user;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;

public interface UserServiceInterface {
    GetAccountsResponse getAccounts(GetAccountsRequest request) throws AbstractException;
}
