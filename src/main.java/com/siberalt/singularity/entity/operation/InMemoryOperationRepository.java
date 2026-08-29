package com.siberalt.singularity.entity.operation;

import com.siberalt.singularity.shared.TimeRange;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryOperationRepository implements OperationRepository {
    private final Map<String, Operation> operationsById = new ConcurrentHashMap<>();

    @Override
    public List<Operation> getByAccountId(String accountId, TimeRange timeRange) {
        return operationsById.values().stream()
            .filter(operation -> operation.accountId().equals(accountId))
            .filter(operation -> withinRange(operation, timeRange))
            .collect(Collectors.toList());
    }

    @Override
    public List<Operation> getByAccountIdAndInstrumentUid(String accountId, String instrumentUid, TimeRange timeRange) {
        return operationsById.values().stream()
            .filter(
                operation -> operation.instrumentUid().equals(instrumentUid) && operation.accountId().equals(accountId)
            )
            .filter(operation -> withinRange(operation, timeRange))
            .collect(Collectors.toList());
    }

    @Override
    public void save(Operation operation) {
        operationsById.put(operation.id(), operation);
    }

    /**
     * Filters by {@code executedDate} when the operation actually executed (it's the more
     * accurate "when did this happen" - a delayed limit order can be created well before it
     * fills). Falls back to {@code date} for operations that never executed (e.g. CANCELED),
     * whose {@code executedDate} is null - otherwise they'd silently drop out of every range.
     */
    private boolean withinRange(Operation operation, TimeRange timeRange) {
        Instant effectiveDate = operation.executedDate() != null ? operation.executedDate() : operation.date();
        return !effectiveDate.isBefore(timeRange.from()) && !effectiveDate.isAfter(timeRange.to());
    }
}
