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

    /**
     * The transaction spec providers price a whole order, so a fill that only covers part of one is
     * priced through a slice - a copy of the order requesting just those lots.
     */
    @Override
    public FillQuote quote(Order order, long lots) {
        Order slice = fillSlice(order, lots);
        Optional<TransactionSpec> orderTransactionSpec = orderTransactionSpecProvider.provide(slice);
        Optional<TransactionSpec> commissionTransactionSpec = commissionTransactionSpecProvider.provide(slice);

        List<TransactionSpec> transactionSpecs = new ArrayList<>();
        List<Quotation> balanceChanges = new ArrayList<>();

        orderTransactionSpec.ifPresent(
            spec -> {
                balanceChanges.add(spec.amount().getQuotation());
                transactionSpecs.add(spec);
            }
        );

        Quotation commission = Quotation.ZERO;

        if (commissionTransactionSpec.isPresent()) {
            commission = commissionTransactionSpec.get().amount().getQuotation();
            balanceChanges.add(commission);
            transactionSpecs.add(commissionTransactionSpec.get());
        }

        return new FillQuote(transactionSpecs, Quotation.sum(balanceChanges), commission);
    }

    /**
     * A copy of the order asking for only the lots this fill covers, priced the same way. Only the
     * fields the spec providers read are carried over.
     */
    protected Order fillSlice(Order order, long lots) {
        return new Order()
            .setAccountId(order.getAccountId())
            .setDirection(order.getDirection())
            .setOrderType(order.getOrderType())
            .setInstrument(order.getInstrument())
            .setInstrumentPrice(order.getInstrumentPrice())
            .setLotsRequested(lots);
    }

    @Override
    public void buy(Order order, long lots, FillQuote quote) throws AbstractException {
        // Refuse a duplicate key before the account is touched - it must not cost money.
        orderRegistry.claimIdempotencyKey(order);

        AccountBalance balance = operationsService.getAccountBalance(order.getAccountId());
        checkTransactionsApplied(balance.applyTransactions(quote.transactionSpecs()));
        operationsService.addToPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            lots * order.getInstrument().getLot()
        );

        settle(order, lots, quote);
    }

    @Override
    public void sell(Order order, long lots, FillQuote quote) throws AbstractException {
        orderRegistry.claimIdempotencyKey(order);

        AccountBalance balance = operationsService.getAccountBalance(order.getAccountId());
        checkTransactionsApplied(balance.applyTransactions(quote.transactionSpecs()));
        operationsService.subtractFromPosition(
            order.getAccountId(),
            order.getInstrument().getUid(),
            lots * order.getInstrument().getLot()
        );

        settle(order, lots, quote);
    }

    /**
     * Records the fill, once the account has actually changed. Marking the order and journalling it
     * happens last on purpose: an order stored as filled is a claim that the trade took place, and
     * since orders are kept, that claim outlives the failure that would have contradicted it.
     */
    protected void settle(Order order, long lots, FillQuote quote) {
        markFilled(order, lots, quote);
        orderRegistry.save(order);
        registerOperations(order, lots, quote);
    }

    /**
     * Adds this fill to the order's running totals. An order that still has lots left to fill stays
     * PARTIALLYFILL - it is not done, and everything that asks for active orders should keep seeing
     * it.
     */
    protected void markFilled(Order order, long lots, FillQuote quote) {
        long lotsExecuted = order.getLotsExecuted() + lots;

        order
            .setExecutedTime(clock.currentTime())
            .setExecutionStatus(
                lotsExecuted < order.getLotsRequested()
                    ? ExecutionStatus.PARTIALLYFILL
                    : ExecutionStatus.FILL
            )
            .setLotsExecuted(lotsExecuted)
            .setBalanceChange(accumulate(order.getBalanceChange(), quote.balanceChange()))
            .setExecutedCommission(accumulate(order.getExecutedCommission(), quote.commission()));
    }

    private Quotation accumulate(Quotation total, Quotation addition) {
        return total == null ? addition : total.add(addition);
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

    /**
     * One operation per transaction of this fill, not of the whole order: an order that fills
     * across several bars leaves a trade in the journal for each of them, the way an account
     * statement shows it.
     */
    protected void registerOperations(Order order, long lots, FillQuote quote) {
        for (TransactionSpec spec : quote.transactionSpecs()) {
            operationRepository.save(toOperation(order, lots, spec, OperationState.EXECUTED));
        }
    }

    protected Operation toOperation(Order order, long lots, TransactionSpec spec, OperationState state) {
        OperationType type = mapTransactionType(spec.type());
        boolean isTrade = type.isBuy() || type.isSell();

        return Operation.builder()
            .id(UUID.randomUUID().toString())
            .accountId(order.getAccountId())
            .instrumentUid(order.getInstrument().getUid())
            .direction(type)
            .quantity(isTrade ? lots : 0)
            .quantityDone(isTrade ? lots : 0)
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
