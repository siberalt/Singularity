package com.siberalt.singularity.strategy.market.position;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.operation.ReadOperationRepository;
import com.siberalt.singularity.shared.TimePointRange;
import com.siberalt.singularity.shared.TimeRange;

import java.lang.ref.SoftReference;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class BaseEntryPriceCalculator implements EntryPriceCalculator {
    private final ReadOperationRepository operationRepository;

    private static final ConcurrentHashMap<String, SoftReference<CachedEntry>> cache = new ConcurrentHashMap<>();

    private record CachedEntry(EntryPrice state, Instant lastProcessedTime) {
    }

    public BaseEntryPriceCalculator(ReadOperationRepository operationRepository) {
        this.operationRepository = operationRepository;
    }

    public EntryPrice calculate(String accountId, String instrumentUid) {
        String key = buildKey(accountId, instrumentUid);

        SoftReference<CachedEntry> ref = cache.get(key);
        CachedEntry cached = (ref != null) ? ref.get() : null;
        List<Operation> operations;
        EntryPrice state;
        EntryPrice initialState;

        if (cached != null) {
            // plusNanos(1) keeps this exclusive of the already-processed checkpoint operation -
            // TimeRange bounds are inclusive, so without it the last-seen operation would be
            // re-applied to the running average on every subsequent call.
            TimeRange sinceCache = new TimeRange(cached.lastProcessedTime().plusNanos(1), Instant.MAX);
            operations = operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, sinceCache);
            initialState = cached.state();
        } else {
            operations = operationRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid, TimeRange.MAX);
            initialState = EntryPrice.EMPTY;
        }

        operations = operations.stream()
            .filter(o -> o.state() == OperationState.EXECUTED)
            .filter(o -> o.direction().isBuy() || o.direction().isSell())
            .filter(o -> o.executedDate() != null) // защита от null
            .sorted(Comparator.comparing(Operation::executedDate))
            .toList();

        if (operations.isEmpty()) {
            return initialState;
        }

        state = aggregateOperations(initialState, operations);

        Instant lastTime = operations.getLast().executedDate();
        cache.put(key, new SoftReference<>(new CachedEntry(state, lastTime)));
        return state;
    }

    private EntryPrice aggregateOperations(EntryPrice previous, List<Operation> operations) {
        EntryPrice state = previous;

        for (Operation operation : operations) {
            state = applyOperation(state, operation);
        }

        return state;
    }

    private EntryPrice applyOperation(EntryPrice previous, Operation operation) {
        long previousQuantity = previous.quantity();
        double previousPrice = previous.averagePrice().toDouble();

        double operationPrice = operation.price().toDouble();
        long operationLots = operation.quantityDone();
        boolean isBuy = operation.direction().isBuy();

        if (previousQuantity == 0) {
            return new EntryPrice(
                isBuy ? operationLots : -operationLots,
                Quotation.of(operationPrice),
                new TimePointRange(new TimePoint(operation.executedDate()))
            );
        }

        if ((previousQuantity > 0 && isBuy) || (previousQuantity < 0 && !isBuy)) {
            long oldVolume = Math.abs(previousQuantity);
            long newVolume = oldVolume + operationLots;
            double newAvg = (previousPrice * oldVolume + operationPrice * operationLots) / newVolume;

            return new EntryPrice(
                previousQuantity > 0 ? newVolume : -newVolume,
                Quotation.of(newAvg),
                TimePointRange.unionByInstants(
                    previous.timePointRange(),
                    new TimePointRange(new TimePoint(operation.executedDate()))
                )
            );
        } else {
            long oppositeVolume = Math.abs(previousQuantity);
            if (operationLots > oppositeVolume) {
                long remaining = operationLots - oppositeVolume;

                return new EntryPrice(
                    isBuy ? remaining : -remaining,
                    Quotation.of(operationPrice),
                    new TimePointRange(new TimePoint(operation.executedDate()))
                );
            } else if (operationLots < oppositeVolume) {
                long newQty = previousQuantity + (isBuy ? operationLots : -operationLots);

                return new EntryPrice(newQty, Quotation.of(previousPrice), previous.timePointRange());
            } else {
                return EntryPrice.EMPTY;
            }
        }
    }

    private String buildKey(String accountId, String instrumentUid) {
        return accountId + "|" + instrumentUid;
    }

    public static void invalidate(String accountId, String instrumentUid) {
        cache.remove(accountId + "|" + instrumentUid);
    }

    public static void clearCache() {
        cache.clear();
    }
}
