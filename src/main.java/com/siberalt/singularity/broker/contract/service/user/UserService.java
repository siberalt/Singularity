package com.siberalt.singularity.broker.contract.service.user;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;

public interface UserService {
    GetAccountsResponse getAccounts(GetAccountsRequest request) throws AbstractException;
}
