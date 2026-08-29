package com.siberalt.singularity.entity.operation;

import com.siberalt.singularity.shared.TimeRange;

import java.util.List;

public interface ReadOperationRepository {
    List<Operation> getByAccountId(String accountId, TimeRange timeRange);
    List<Operation> getByAccountIdAndInstrumentUid(String accountId, String instrumentUid, TimeRange timeRange);
}
