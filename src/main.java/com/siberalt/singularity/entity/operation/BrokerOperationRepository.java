package com.siberalt.singularity.entity.operation;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import com.siberalt.singularity.broker.contract.service.operation.request.GetOperationsRequest;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.strategy.context.Clock;
import com.siberalt.singularity.strategy.context.execution.time.RealTimeClock;

import java.time.Instant;
import java.util.List;

/**
 * Adapts a broker's {@link OperationsService#getOperations} into {@link ReadOperationRepository},
 * so consumers coded against the entity-level repository abstraction (e.g. the position-tracking
 * calculators) can be backed by a live broker's real operation history instead of only the mock
 * broker's {@link InMemoryOperationRepository}.
 *
 * <p>A live broker's {@code getOperations} needs a bounded {@code from}/{@code to} range - it
 * can't be queried with {@link TimeRange#MAX} as-is. Rather than reject it, the requested range
 * is clamped: {@code to} down to "now", and {@code from} up to {@link #EARLIEST_QUERYABLE} (a
 * broad floor predating any realistic account). Passing {@link TimeRange#MAX} is exactly how a
 * caller asks for "everything" - clamping is this adapter's job of making that meaningful for a
 * live broker, not the caller's.</p>
 */
public class BrokerOperationRepository implements ReadOperationRepository {
    private static final Instant EARLIEST_QUERYABLE = Instant.parse("2000-01-01T00:00:00Z");

    private final OperationsService operationsService;
    private final Clock clock;

    public BrokerOperationRepository(OperationsService operationsService) {
        this(operationsService, new RealTimeClock());
    }

    public BrokerOperationRepository(OperationsService operationsService, Clock clock) {
        this.operationsService = operationsService;
        this.clock = clock;
    }

    @Override
    public List<Operation> getByAccountId(String accountId, TimeRange timeRange) {
        return fetch(accountId, clamp(timeRange));
    }

    @Override
    public List<Operation> getByAccountIdAndInstrumentUid(String accountId, String instrumentUid, TimeRange timeRange) {
        return fetch(accountId, clamp(timeRange)).stream()
            .filter(operation -> operation.instrumentUid().equals(instrumentUid))
            .toList();
    }

    private TimeRange clamp(TimeRange timeRange) {
        Instant now = clock.currentTime();
        Instant from = timeRange.from().isBefore(EARLIEST_QUERYABLE) ? EARLIEST_QUERYABLE : timeRange.from();
        Instant to = timeRange.to().isAfter(now) ? now : timeRange.to();

        return new TimeRange(from, to);
    }

    private List<Operation> fetch(String accountId, TimeRange timeRange) {
        try {
            return operationsService.getOperations(
                new GetOperationsRequest()
                    .setAccountId(accountId)
                    .setFrom(timeRange.from())
                    .setTo(timeRange.to())
            ).getOperations().stream().toList();
        } catch (AbstractException e) {
            throw new RuntimeException(e);
        }
    }
}
