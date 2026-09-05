package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.order.CommissionTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.OrderTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.TransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.response.ExecutionStatus;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.operation.AccountBalance;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationRepository;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.operation.OperationType;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.transaction.Transaction;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.entity.transaction.TransactionStatus;
import com.siberalt.singularity.entity.transaction.TransactionType;
import com.siberalt.singularity.strategy.context.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DefaultOrderExecutor implements OrderExecutor {
    public static final double DEFAULT_COMMISSION_RATIO = 0.003;

    protected final Clock clock;
    protected final MockOperationsService operationsService;
    protected final OrderRegistry orderRegistry;
    protected final OperationRepository operationRepository;
    protected final TransactionSpecProvider commissionTransactionSpecProvider;
    protected final TransactionSpecProvider orderTransactionSpecProvider;

    public DefaultOrderExecutor(
        Clock clock,
        MockOperationsService operationsService,
        OrderRegistry orderRegistry,
        OperationRepository operationRepository
    ) {
        this(
            clock,
            operationsService,
            orderRegistry,
            operationRepository,
            new CommissionTransactionSpecProvider(DEFAULT_COMMISSION_RATIO),
            new OrderTransactionSpecProvider()
        );
    }

    public DefaultOrderExecutor(
        Clock clock,
        MockOperationsService operationsService,
        OrderRegistry orderRegistry,
        OperationRepository operationRepository,
        TransactionSpecProvider commissionTransactionSpecProvider,
        TransactionSpecProvider orderTransactionSpecProvider
    ) {
        this.clock = clock;
        this.operationsService = operationsService;
        this.orderRegistry = orderRegistry;
        this.operationRepository = operationRepository;
        this.commissionTransactionSpecProvider = commissionTransactionSpecProvider;
        this.orderTransactionSpecProvider = orderTransactionSpecProvider;
    }

    @Override
    public List<TransactionSpec> calculateTransactions(Order order) {
        Optional<TransactionSpec> commissionTransactionSpec = commissionTransactionSpecProvider.provide(order);
        Optional<TransactionSpec> orderTransactionSpec = orderTransactionSpecProvider.provide(order);

        List<TransactionSpec> transactionSpecs = new ArrayList<>();
        List<Quotation> balanceChanges = new ArrayList<>();

        orderTransactionSpec.ifPresent(
            spec -> {
                Quotation amount = spec.amount().getQuotation();
                balanceChanges.add(amount);
                order.setExecutedCommission(amount);
                transactionSpecs.add(spec);
            }
        );
        commissionTransactionSpec.ifPresent(
            spec -> {
                balanceChanges.add(spec.amount().getQuotation());
                transactionSpecs.add(spec);
            }
        );
        order.setBalanceChange(Quotation.sum(balanceChanges));

        return transactionSpecs;
    }

    @Override
    public void buy(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException {
        // Refuse a duplicate key before the account is touched - it must not cost money.
        orderRegistry.claimIdempotencyKey(order);

        AccountBalance balance = operationsService.getAccountBalance(order.getAccountId());
        checkTransactionsApplied(balance.applyTransactions(transactionSpecs));
        operationsService.addToPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            order.getLotsRequested() * order.getInstrument().getLot()
        );

        settle(order, transactionSpecs);
    }

    @Override
    public void sell(Order order, List<TransactionSpec> transactionSpecs) throws AbstractException {
        orderRegistry.claimIdempotencyKey(order);

        AccountBalance balance = operationsService.getAccountBalance(order.getAccountId());
        checkTransactionsApplied(balance.applyTransactions(transactionSpecs));
        operationsService.subtractFromPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            order.getLotsRequested() * order.getInstrument().getLot()
        );

        settle(order, transactionSpecs);
    }

    /**
     * Records the fill, once the account has actually changed. Marking the order filled and
     * journalling it happens last on purpose: an order stored as FILL is a claim that the trade
     * took place, and since orders are kept, that claim outlives the failure that would have
     * contradicted it.
     */
    protected void settle(Order order, List<TransactionSpec> transactionSpecs) {
        markFilled(order);
        orderRegistry.save(order);
        registerOperations(order, transactionSpecs);
    }

    protected void markFilled(Order order) {
        order
            .setExecutedTime(clock.currentTime())
            .setExecutionStatus(ExecutionStatus.FILL)
            .setLotsExecuted(order.getLotsRequested());
    }

    /**
     * A transaction can still be rejected by the balance itself (an overdraft the pre-checks did
     * not catch). Applying the rest of the fill - the position change, the operations - on top of a
     * rejected transaction would leave the account inconsistent, so fail the order instead.
     */
    protected void checkTransactionsApplied(List<Transaction> transactions) throws AbstractException {
        for (Transaction transaction : transactions) {
            if (transaction.getStatus() == TransactionStatus.FAILED) {
                throw ExceptionBuilder
                    .newBuilder(ErrorCode.INSUFFICIENT_BALANCE)
                    .withMessage(transaction.getErrorMessage())
                    .build();
            }
        }
    }

    protected void registerOperations(Order order, List<TransactionSpec> transactionSpecs) {
        for (TransactionSpec spec : transactionSpecs) {
            operationRepository.save(toOperation(order, spec, OperationState.EXECUTED));
        }
    }

    protected Operation toOperation(Order order, TransactionSpec spec, OperationState state) {
        OperationType type = mapTransactionType(spec.type());
        boolean isTrade = type.isBuy() || type.isSell();

        return Operation.builder()
            .id(UUID.randomUUID().toString())
            .accountId(order.getAccountId())
            .instrumentUid(order.getInstrument().getUid())
            .direction(type)
            .quantity(isTrade ? order.getLotsRequested() : 0)
            .quantityDone(isTrade ? order.getLotsExecuted() : 0)
            .price(isTrade ? order.getInstrumentPrice() : null)
            .payment(spec.amount().getQuotation())
            .state(state)
            .date(order.getCreatedTime())
            .executedDate(order.getExecutedTime())
            .build();
    }

    protected OperationType mapTransactionType(TransactionType type) {
        return switch (type) {
            case BUY -> OperationType.BUY;
            case SELL -> OperationType.SELL;
            case COMMISSION -> OperationType.BROKER_FEE;
            default -> OperationType.UNSPECIFIED;
        };
    }
}
