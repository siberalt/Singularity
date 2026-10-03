package com.siberalt.singularity.broker.impl.mock.shared.operation;

import com.siberalt.singularity.broker.contract.service.margin.MarginContext;
import com.siberalt.singularity.broker.contract.service.margin.MarginRequirement;
import com.siberalt.singularity.entity.position.Position;

import java.time.Instant;

/**
 * What the balance it belongs to is worth, and how much of it the broker has claimed as cover.
 * <p>
 * A component of {@link AccountBalance} rather than a service beside it, because every number here is a
 * property of that balance's own money and positions. It first lived outside, reaching back in for the
 * positions and the cash - which left the balance holding a loose {@code creditAllowed} flag while
 * something else decided what credit meant. Now the one object answers both: a balance with a margin is a
 * margin account, and whether it may go into credit is this component's business.
 * <p>
 * Three numbers and the arithmetic between them. <b>Equity</b> is the money plus the market value of every
 * position, and a short counts negative - it is a debt in shares, so it subtracts. <b>Used</b> is what the
 * broker demands be covered: each position's value times its risk rate, long and short rates being
 * different. <b>Free</b> is equity minus used, and an order is allowed exactly when free would still be
 * non-negative after it.
 * <p>
 * Why that rule and not "is there enough money": buying with borrowed cash and selling shares one does not
 * own are the same operation seen from two sides, and both are refused by the same inequality. A cash
 * balance cannot express either - it says a purchase is impossible when the account is fully invested, and
 * says nothing at all about a short.
 * <p>
 * Arithmetic is in doubles, unlike the ledger's quotations. These numbers answer a comparison, never a
 * balance: nothing computed here is ever written to an account, so a rounding error far below a lot of
 * anything is cheaper than making the margin check invent kopeks.
 */
public class Margin {
    private final AccountBalance balance;
    private final MarginContext context;
    private final boolean creditAllowed;
    private Instant accruedAt;

    public Margin(AccountBalance balance, MarginContext context, boolean creditAllowed) {
        if (balance == null) {
            throw new IllegalArgumentException("Маржа считается по конкретному счёту");
        }

        this.balance = balance;
        this.context = context == null ? MarginContext.NONE : context;
        this.creditAllowed = creditAllowed;
    }

    /** Whether the money balance may go below zero - a leveraged long is exactly that. */
    public boolean isCreditAllowed() {
        return creditAllowed;
    }

    /** Money plus the market value of every position, shorts negative. */
    public double equity(String currencyIso) {
        double equity = balance.getAvailableMoney(currencyIso).getQuotation().toDouble();

        for (Position position : balance.getPositions()) {
            equity += position.getBalance() * context.priceOf(position.getInstrumentUid());
        }

        return equity;
    }

    /** What the broker demands be covered to hold what is held now, at the initial rates. */
    public double used() {
        return claimed(true, null, 0);
    }

    /** The same at the maintenance rates - the level that decides whether a position may stand. */
    public double maintenance() {
        return claimed(false, null, 0);
    }

    public double free(String currencyIso) {
        return equity(currencyIso) - used();
    }

    /** Whether what is held has stopped being covered - the condition for a margin call. */
    public boolean breachesMaintenance(String currencyIso) {
        return shortfall(currencyIso) > 0;
    }

    /**
     * How much cover is missing, zero when none is. This is what a margin call has to make disappear, and
     * it does not fall by the value of what is sold: selling frees the whole of that position's
     * requirement, so closing a position worth {@code v} at rate {@code d} removes {@code v * d} of the
     * shortfall while leaving equity where it was.
     */
    public double shortfall(String currencyIso) {
        return Math.max(0, maintenance() - equity(currencyIso));
    }

    /**
     * Money on loan from the broker: a cash balance below zero and nothing else. A long bought partly with
     * borrowed money is exactly this, and it is what interest is charged on.
     */
    public double borrowedMoney(String currencyIso) {
        return Math.max(0, -balance.getAvailableMoney(currencyIso).getQuotation().toDouble());
    }

    /**
     * Shares on loan, valued at the market: the worth of every short position. This is what a borrow fee is
     * charged on, and it is quite separate from borrowed money - an account can be short while holding cash,
     * and then it pays for the stock and not for the cash.
     */
    public double borrowedShares() {
        double borrowed = 0;

        for (Position position : balance.getPositions()) {
            if (position.getBalance() < 0) {
                borrowed += -position.getBalance() * context.priceOf(position.getInstrumentUid());
            }
        }

        return borrowed;
    }

    /** When funding was last charged up to; null until the first accrual sets it. */
    public Instant getAccruedAt() {
        return accruedAt;
    }

    public Margin setAccruedAt(Instant accruedAt) {
        this.accruedAt = accruedAt;

        return this;
    }

    /**
     * Whether a trade may go ahead: would the account still cover everything it holds once it had filled.
     * <p>
     * The commission is part of it, and leaving it out was a mistake worth recording: an account under full
     * cover would then buy exactly its own money's worth and go a few kopeks into credit paying the fee,
     * which is precisely what full cover is supposed to forbid. It is small next to the cover being
     * computed, but "small" is not "absent" at the boundary, and the boundary is the only place this
     * function is ever asked about.
     *
     * @param instrumentUid what is being traded
     * @param currencyIso   the currency the account is judged in
     * @param shares        how many shares would be bought, negative for a sale
     * @param price         what they would go through at
     * @param commission    what the fill would cost in fees, as a positive amount
     */
    public boolean covers(String instrumentUid, String currencyIso, long shares, double price,
                          double commission) {
        // Cash leaves at the fill price and the shares arrive marked at the current one, so the two cancel
        // only when a fill goes through at the mark. Buy above it and equity drops by the difference there
        // and then - which is the honest cost of a bad fill and the reason this is not written as "equity
        // does not change".
        double equity = equity(currencyIso) - shares * price + shares * context.priceOf(instrumentUid)
            - Math.abs(commission);

        return equity >= claimed(true, instrumentUid, shares);
    }

    /**
     * The cover demanded of every position, optionally with one of them changed by {@code shares}.
     *
     * @param initial  initial rates when true, maintenance rates when false
     * @param changing the instrument whose position the caller is about to change, or null
     * @param shares   how many shares that position would change by, signed
     */
    private double claimed(boolean initial, String changing, long shares) {
        double claimed = 0;
        boolean seen = false;

        for (Position position : balance.getPositions()) {
            long held = position.getBalance();

            if (position.getInstrumentUid().equals(changing)) {
                held += shares;
                seen = true;
            }

            claimed += claimedOn(position.getInstrumentUid(), held, initial);
        }

        return seen || changing == null ? claimed : claimed + claimedOn(changing, shares, initial);
    }

    /** What one position of this size costs in cover. */
    private double claimedOn(String instrumentUid, long held, boolean initial) {
        if (held == 0) {
            return 0;
        }

        MarginRequirement rates = context.requirementOf(instrumentUid);
        double rate = initial ? rates.initial(held > 0) : rates.maintenance(held > 0);

        return Math.abs(held * context.priceOf(instrumentUid)) * rate;
    }
}
