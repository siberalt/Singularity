package com.siberalt.singularity.broker.impl.tinkoff.shared;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.market.request.GetCurrentPriceRequest;
import com.siberalt.singularity.broker.contract.service.order.CommissionTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.OrderTransactionSpecProvider;
import com.siberalt.singularity.broker.contract.service.order.TransactionService;
import com.siberalt.singularity.broker.contract.service.order.request.*;
import com.siberalt.singularity.broker.contract.service.order.response.*;
import com.siberalt.singularity.broker.impl.tinkoff.shared.exception.ExceptionConverter;
import com.siberalt.singularity.broker.impl.tinkoff.shared.translation.*;
import com.siberalt.singularity.broker.shared.ListTranslator;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.transaction.Transaction;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.strategy.context.execution.time.RealTimeClock;
import ru.tinkoff.piapi.contract.v1.OrderExecutionReportStatus;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.core.OrdersService;

import java.util.Collections;
import java.util.List;

import static com.siberalt.singularity.broker.impl.tinkoff.shared.translation.OrderStateTranslator.calculateCommission;

public class OrderService implements com.siberalt.singularity.broker.contract.service.order.OrderService {
    public static final double DEFAULT_COMMISSION_RATE = 0.003; // 0.3% commission rate
    private final OrdersService ordersServiceApi;
    private final AbstractTinkoffBroker broker;
    private final TransactionService transactionService;
    private final CommissionTransactionSpecProvider commissionTransactionSpecProvider;

    public OrderService(OrdersService ordersServiceApi, AbstractTinkoffBroker broker) {
        this.ordersServiceApi = ordersServiceApi;
        this.broker = broker;
        this.commissionTransactionSpecProvider = new CommissionTransactionSpecProvider(DEFAULT_COMMISSION_RATE);
        this.transactionService = new TransactionService()
            .addProvider(new OrderTransactionSpecProvider())
            .addProvider(commissionTransactionSpecProvider);
    }

    public OrderService setCommissionRate(double commissionRate) {
        commissionTransactionSpecProvider.setCommissionRatio(commissionRate);
        return this;
    }

    @Override
    public CalculateResponse calculate(CalculateRequest request) throws AbstractException {
        Order order = createOrder(request.getPostOrderRequest());
        List<TransactionSpec> transactionSpecs = transactionService.calculateSpecs(order);
        order.setBalanceChange(transactionService.sumSpecs(transactionSpecs));

        return new CalculateResponse(
            order.getInstrument().getUid(),
            order.getBalanceChange(),
            order.getInstrumentPrice(),
            order.getLotsRequested(),
            transactionSpecs
        );
    }

    @Override
    public PostOrderResponse post(PostOrderRequest request) throws AbstractException {
        var price = request.getPrice() == null ? Quotation.newBuilder().build() : QuotationTranslator.toTinkoff(request.getPrice());
        var response = ExceptionConverter.rethrowContractExceptionOnError(() -> ordersServiceApi.postOrderSync(
            request.getInstrumentId(), request.getQuantity(), price,
            OrderDirectionTranslator.toTinkoff(request.getDirection()),
            request.getAccountId(), OrderTypeTranslator.toTinkoff(request.getOrderType()),
            request.getIdempotencyKey()
        ));
        return toContractPostOrderResponse(request.getAccountId(), response);
    }

    @Override
    public CancelOrderResponse cancel(CancelOrderRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() -> ordersServiceApi.cancelOrderSync(
            request.getAccountId(), request.getOrderId()
        ));
        return new CancelOrderResponse().setTime(response);
    }

    @Override
    public OrderState getState(GetOrderStateRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(() -> ordersServiceApi.getOrderStateSync(
            request.getAccountId(), request.getOrderId()
        ));
        return OrderStateTranslator.toContract(response);
    }

    @Override
    public GetOrdersResponse get(GetOrdersRequest request) throws AbstractException {
        var response = ExceptionConverter.rethrowContractExceptionOnError(
            () -> ordersServiceApi.getOrdersSync(request.getAccountId())
        );

        return new GetOrdersResponse().setOrders(ListTranslator.translate(response, OrderStateTranslator::toContract));
    }

    private Order createOrder(PostOrderRequest request) throws AbstractException {
        Instrument instrument = broker.getInstrumentService().get(GetRequest.of(request.getInstrumentId())).getInstrument();
        var currentPrice = broker
            .getMarketDataService()
            .getCurrentPrice(new GetCurrentPriceRequest(request.getInstrumentId())).getPrice();

        return new Order()
            .setLotsRequested(request.getQuantity())
            .setAccountId(request.getAccountId())
            .setDirection(request.getDirection())
            .setOrderType(request.getOrderType())
            .setInstrument(instrument)
            .setInstrumentPrice(currentPrice);
    }

    protected PostOrderResponse toContractPostOrderResponse(
        String accountId,
        ru.tinkoff.piapi.contract.v1.PostOrderResponse response
    ) {
        List<Transaction> transactions;

        if (response.getExecutionReportStatus() == OrderExecutionReportStatus.EXECUTION_REPORT_STATUS_FILL) {
            List<TransactionSpec> transactionSpecs = List.of(
                calculateCommission(
                    response.getExecutedCommission(),
                    response.getInitialCommission()
                )
            );
            transactions = transactionService.create(
                transactionSpecs,
                accountId,
                "tinkoff",
                new RealTimeClock()
            );
        } else {
            transactions = Collections.emptyList();
        }

        return new PostOrderResponse()
            .setOrderId(response.getOrderId())
            .setExecutionStatus(OrderExecutionReportStatusTranslator.toContract(response.getExecutionReportStatus()))
            .setLotsRequested(response.getLotsRequested())
            .setLotsExecuted(response.getLotsExecuted())
            .setTotalBalanceChange(MoneyValueTranslator.toContract(response.getTotalOrderAmount()))
            .setTransactions(transactions)
            .setAciValue(MoneyValueTranslator.toContract(response.getAciValue()))
            .setDirection(OrderDirectionTranslator.toContract(response.getDirection()))
            .setInstrumentPrice(MoneyValueTranslator.toContract(response.getInitialSecurityPrice()))
            .setOrderType(OrderTypeTranslator.toContract(response.getOrderType()))
            .setMessage(response.getMessage())
            .setInstrumentUid(response.getInstrumentUid())
            .setIdempotencyKey(response.getOrderRequestId());
    }
}
