package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.operation.OperationType;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.OrderRepository;

import java.util.UUID;

/**
 * The write side of the order journal. Orders are kept for the life of the broker, in whatever
 * state they reached - a filled or cancelled order stays queryable through
 * {@link MockOrderService#getState}, the way a real broker reports it, and a second cancel of an
 * already-cancelled order can be told apart from one that never existed.
 * <p>
 * Shared by everything that writes an order's lifecycle: {@link DefaultOrderExecutor} on a fill,
 * {@link SimulatedPendingOrderHandler} when it parks an order to wait for the market, and
 * {@link MockOrderService} on cancellation.
 */
public class OrderRegistry {
    private final OrderRepository orderRepository;
    private final OperationRepository operationRepository;

    public OrderRegistry(OrderRepository orderRepository, OperationRepository operationRepository) {
        this.orderRepository = orderRepository;
        this.operationRepository = operationRepository;
    }

    /**
     * Claims the order's idempotency key and stores it. Kept as two steps as well
     * ({@link #claimIdempotencyKey} then {@link #save}) so a caller that changes the account can
     * do the claim - the part that can be refused - before touching anything.
     */
    public void register(Order order) throws AbstractException {
        claimIdempotencyKey(order);
        save(order);
    }

    /**
     * Assigns an idempotency key if the caller did not supply one, and refuses a key that already
     * belongs to a different order. Stores nothing.
     */
    public void claimIdempotencyKey(Order order) throws AbstractException {
        if (order.getIdempotencyKey() == null) {
            order.setIdempotencyKey(UUID.randomUUID().toString());

            return;
        }

        Order existingOrder = orderRepository.getByIdempotencyKey(order.getIdempotencyKey());

        // The same order is re-registered as it moves through its states (parked, then filled);
        // only a different order reusing the key is a duplicate.
        if (existingOrder != null && !existingOrder.getId().equals(order.getId())) {
            throw ExceptionBuilder.create(ErrorCode.DUPLICATE_ORDER);
        }
    }

    public void save(Order order) {
        orderRepository.save(order);
    }

    /**
     * Cancelling stops an order; it does not undo it. Whatever already filled stays filled and
     * stays counted, so the lots executed are left alone - only the order's own status changes.
     */
    public void cancel(Order order) {
        order.setExecutionStatus(ExecutionStatus.CANCELLED);
        operationRepository.save(toCancelOperation(order));
        orderRepository.save(order);
    }

    protected Operation toCancelOperation(Order order) {
        OperationType type = order.getDirection().isBuy() ? OperationType.BUY : OperationType.SELL;

        return Operation.builder()
            .id(UUID.randomUUID().toString())
            .accountId(order.getAccountId())
            .instrumentUid(order.getInstrument().getUid())
            .direction(type)
            .quantity(order.getLotsRequested())
            .quantityDone(order.getLotsExecuted())
            .price(order.getInstrumentPrice())
            .payment(Quotation.ZERO)
            .state(OperationState.CANCELED)
            .date(order.getCreatedTime())
            .executedDate(order.getExecutedTime())
            .build();
    }
}
