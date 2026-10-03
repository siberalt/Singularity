package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.margin.FundingPolicy;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.order.request.OrderDirection;
import com.siberalt.singularity.broker.contract.service.order.request.OrderType;
import com.siberalt.singularity.broker.contract.service.order.request.PostOrderRequest;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.mock.shared.operation.AccountBalance;
import com.siberalt.singularity.broker.impl.mock.shared.operation.Margin;
import com.siberalt.singularity.entity.instrument.Instrument;
import com.siberalt.singularity.entity.position.Position;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.entity.transaction.TransactionType;
import com.siberalt.singularity.strategy.context.Clock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What happens to a margin account as time passes: the loan is charged for, and if the cover has run out,
 * positions are closed until it has not.
 * <p>
 * These are the two things a margin account does that a cash one does not, and both need a moment to happen
 * at. The cover check needs none - it answers a question about an order - but funding accrues over hours
 * and a margin call is triggered by a price, so something has to say "it is now later". That is what
 * {@link #settle} is: it brings the account up to date, and it is called before every cover check, after
 * every fill, and by whoever is driving the simulation whenever it wants the account marked.
 * <p>
 * Between those moments nothing accrues and no call fires. A position that breaches its maintenance level
 * at noon and recovers by one o'clock is never liquidated if nobody settled in between - a real broker
 * watches continuously and this does not. The honest way to use it is to settle on every bar, which is what
 * a simulation holding positions overnight must do anyway to pay for them; the lazy path exists so that a
 * study which only trades is still charged correctly over the stretches it held something.
 * <p>
 * Funding is charged as two separate things because they are two separate loans. <b>Interest</b> on money
 * borrowed - a cash balance below zero, which is what a leveraged long is. <b>A fee</b> on shares borrowed -
 * the market value of the shorts. An account can owe one without the other, and a tariff prices them
 * differently, so they are charged by two policies and land in the ledger as two kinds of transaction.
 */
public class MarginSettlement {
    private final Clock clock;
    private final MockOperationsService operationsService;
    private final MockInstrumentService instrumentService;
    private final MockOrderService orderService;
    private FundingPolicy moneyFunding = FundingPolicy.FREE;
    private FundingPolicy stockFunding = FundingPolicy.FREE;
    private boolean callsMargin = true;

    public MarginSettlement(
        Clock clock,
        MockOperationsService operationsService,
        MockInstrumentService instrumentService,
        MockOrderService orderService
    ) {
        this.clock = clock;
        this.operationsService = operationsService;
        this.instrumentService = instrumentService;
        this.orderService = orderService;
    }

    /** What borrowed money costs - interest on a negative cash balance. */
    public MarginSettlement setMoneyFunding(FundingPolicy moneyFunding) {
        this.moneyFunding = moneyFunding == null ? FundingPolicy.FREE : moneyFunding;

        return this;
    }

    /** What borrowed shares cost - the fee on the market value of the shorts. */
    public MarginSettlement setStockFunding(FundingPolicy stockFunding) {
        this.stockFunding = stockFunding == null ? FundingPolicy.FREE : stockFunding;

        return this;
    }

    /**
     * Whether a breach of the maintenance level closes positions. Off, the account simply stands
     * uncovered - which is what a study measuring drawdowns rather than managing them wants, and what the
     * mock broker did before any of this existed.
     */
    public MarginSettlement setCallsMargin(boolean callsMargin) {
        this.callsMargin = callsMargin;

        return this;
    }

    /**
     * Brings the account up to the current moment: charges what the loan has cost since the last
     * settlement, then liquidates if the cover has run out.
     * <p>
     * In that order, and the order matters: funding is a cost, so charging it can be what pushes an account
     * through its maintenance level. Calling the margin first would miss exactly the breaches the fee
     * caused.
     *
     * @return how many positions were closed by a margin call, zero when none were
     */
    public int settle(String accountId, String currencyIso) throws AbstractException {
        AccountBalance balance = operationsService.getAccountBalance(accountId);
        Margin margin = balance.getMargin();

        if (margin == null) {
            return 0;
        }

        accrue(balance, margin, currencyIso);

        return callsMargin ? call(accountId, balance, margin, currencyIso) : 0;
    }

    /** Charges interest on borrowed money and a fee on borrowed shares for the time since last asked. */
    protected void accrue(AccountBalance balance, Margin margin, String currencyIso) {
        Instant now = clock.currentTime();
        Instant since = margin.getAccruedAt();

        margin.setAccruedAt(now);

        if (since == null || !now.isAfter(since)) {
            return;
        }

        List<TransactionSpec> charges = new ArrayList<>();
        double money = margin.borrowedMoney(currencyIso);
        double stock = margin.borrowedShares();

        if (money > 0) {
            charges.add(new TransactionSpec(TransactionType.INTEREST, "Interest on borrowed money",
                moneyFunding.over(Money.of(currencyIso, money), since, now)));
        }

        if (stock > 0) {
            charges.add(new TransactionSpec(TransactionType.FEE, "Fee on borrowed shares",
                stockFunding.over(Money.of(currencyIso, stock), since, now)));
        }

        if (!charges.isEmpty()) {
            balance.applyTransactions(charges);
        }
    }

    /**
     * Closes positions until the account is covered again, largest cover relief first.
     * <p>
     * Largest first because it ends the breach in the fewest trades, which is what a broker's own
     * liquidation does and what costs the account least in commission. It is not what a client would choose
     * - they would sell what they like least - and that difference is a reason not to read a liquidated
     * run as a strategy's result.
     *
     * @return how many positions were closed
     */
    protected int call(String accountId, AccountBalance balance, Margin margin, String currencyIso)
        throws AbstractException {
        // A snapshot, walked once. Looping on the breach instead would never end if a position cannot be
        // closed - and one that is smaller than a lot cannot be. Each position is closed at most once, so
        // the worst case is "everything sold and still uncovered", which is a deficit a real broker would
        // pursue as a debt and all this can do is leave it visible.
        List<Position> open = new ArrayList<>(balance.getPositions()).stream()
            .filter(position -> position.getBalance() != 0)
            .sorted(Comparator.comparingDouble(position -> -Math.abs(position.getBalance())))
            .toList();
        int closed = 0;

        for (Position position : open) {
            if (!margin.breachesMaintenance(currencyIso)) {
                break;
            }

            close(accountId, position);
            closed++;
        }

        return closed;
    }

    /** Buys back a short or sells out a long, at the market, in one order. */
    private void close(String accountId, Position position) throws AbstractException {
        Instrument instrument = instrumentService.get(GetRequest.of(position.getInstrumentUid()))
            .getInstrument();
        long lots = Math.abs(position.getBalance()) / Math.max(1, instrument.getLot());

        if (lots <= 0) {
            // Less than a lot left: it cannot be traded, so it cannot be liquidated either. Zeroing the
            // position would invent a fill, so the remainder is left to stand.
            position.setBalance(0);

            return;
        }

        orderService.postForLiquidation(new PostOrderRequest()
            .setAccountId(accountId)
            .setInstrumentId(position.getInstrumentUid())
            .setQuantity(lots)
            .setOrderType(OrderType.MARKET)
            .setDirection(position.getBalance() > 0 ? OrderDirection.SELL : OrderDirection.BUY));
    }
}
