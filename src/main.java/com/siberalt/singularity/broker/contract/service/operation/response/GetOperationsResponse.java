package com.siberalt.singularity.broker.contract.service.operation.response;

import com.siberalt.singularity.entity.operation.Operation;

import java.util.Collection;

public class GetOperationsResponse {
    protected Collection<Operation> operations;

    public Collection<Operation> getOperations() {
        return operations;
    }

    public GetOperationsResponse setOperations(Collection<Operation> operations) {
        this.operations = operations;
        return this;
    }
}
