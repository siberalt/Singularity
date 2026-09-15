package com.siberalt.singularity.broker.impl.decorator;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.operation.ReadOperationRepository;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stops trading one instrument once the profit made on it has been handed back, and lets it start
 * again after a while.
 * <p>
 * It guards what a stop on a position cannot reach. A stop asks whether this trade has gone wrong;
 * this asks whether the run of trades has. A strategy can give a made profit back through fifty
 * small losses no single one of which any stop would have caught - the strategy measured here drew
 * down twenty-seven percent while its worst hour cost seven.
 * <p>
 * What it watches is this instrument's own profit and loss - the cash its operations have moved plus
 * what its position is worth - and not the account. An account holds other instruments, possibly
 * doing well; a loss here is no reason to sell those, and a threshold read against the whole capital
 * fires at the wrong time for a position that is a fraction of it.
 * <p>
 * The rule is a give-back rather than a drawdown, which is what makes it lock profit in. Having made
 * a hundred it may hand back {@code giveBack} of it and no more, so every new high ratchets the
 * floor up behind it. Before there is any profit to give back there is a loss budget instead, a
 * share of the capital this instrument was allowed - otherwise the first tick of loss against a peak
 * of nothing would already be an infinite give-back.
 * <p>
 * What lets it start again is the mark moving down to meet the curve, and that part is not a choice:
 * out of the market the curve is flat, so a give-back measured against a high water mark never
 * shrinks and a guard waiting for it to shrink never lifts. Were the mark not reset, the same loss
 * would stop it again on the first bar back.
 * <p>
 * The wait before that happens is a choice, and a small one. All it buys is that the position is not
 * handed straight back to a signal that has not changed its mind since it lost the money, at the
 * price of a round trip. Nothing about a give-back says how long that should be, so it is a knob to
 * be measured rather than a number to be believed, and the default is one day.
 * <p>
 * While it holds, an open position is closed - a guard that only refused new trades would leave the
 * one already losing - and nothing new is opened until the wait is out.
 * <p>
 * It wraps the signal rather than sitting beside it in a switch. A switch hands over when the
 * priority calculator speaks loudly enough, and there is no reading of a signal that means "stay
 * out": silence there is read as "ask the other one".
 */
public class ProfitGiveBackUpsideCalculator implements UpsideCalculator {
    public static final double DEFAULT_GIVE_BACK = 0.3;
    public static final double DEFAULT_LOSS_BUDGET = 0.1;
    public static final Duration DEFAULT_WAIT = Duration.ofDays(1);

    private final UpsideCalculator delegate;
    private final String accountId;
    private final String instrumentUid;
    private final ReadOperationRepository operations;
    private final double allocated;
    private final double giveBack;
    private final double lossBudget;
    private final Duration wait;

    private final Book book = new Book();
    private final Set<String> foldedAtBoundary = new HashSet<>();
    private Instant folded = Instant.EPOCH;

    private double peak;
    private Instant guardedSince;
    private long trips;

    public ProfitGiveBackUpsideCalculator(
        UpsideCalculator delegate,
        String accountId,
        String instrumentUid,
        ReadOperationRepository operations,
        Money allocated
    ) {
        this(delegate, accountId, instrumentUid, operations, allocated,
            DEFAULT_GIVE_BACK, DEFAULT_LOSS_BUDGET, DEFAULT_WAIT);
    }

    /**
     * @param allocated  the capital this instrument trades on, which the loss budget is read against
     * @param giveBack   how much of the profit made here may be handed back before trading stops, as
     *                   a share of the best that profit has been
     * @param lossBudget how much may be lost while there is no profit to give back, as a share of
     *                   the allocated capital
     * @param wait       how long it stays stopped
     */
    public ProfitGiveBackUpsideCalculator(
        UpsideCalculator delegate,
        String accountId,
        String instrumentUid,
        ReadOperationRepository operations,
        Money allocated,
        double giveBack,
        double lossBudget,
        Duration wait
    ) {
        if (delegate == null || operations == null || allocated == null) {
            throw new IllegalArgumentException("A guard needs a signal to guard and a book to watch");
        }

        if (giveBack <= 0 || giveBack > 1) {
            throw new IllegalArgumentException(
                "A share of profit to hand back lies between nothing and all of it, got " + giveBack);
        }

        if (lossBudget <= 0) {
            throw new IllegalArgumentException(
                "A guard that allows no loss at all never lets a position breathe, got " + lossBudget);
        }

        if (wait == null || wait.isNegative()) {
            throw new IllegalArgumentException("A wait cannot be negative");
        }

        this.delegate = delegate;
        this.accountId = accountId;
        this.instrumentUid = instrumentUid;
        this.operations = operations;
        this.allocated = allocated.getQuotation().toDouble();
        this.giveBack = giveBack;
        this.lossBudget = lossBudget;
        this.wait = wait;
    }

    /** How many times the guard has stopped this instrument - the measure of a guard meant to be rare. */
    public long getTrips() {
        return trips;
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Upside.NEUTRAL;
        }

        Candle last = lastCandles.getLast();
        fold(last.getTime());
        double profit = book.cash + book.position * last.getCloseAsDouble();

        if (guardedSince != null) {
            if (last.getTime().isBefore(guardedSince.plus(wait))) {
                return closing(book.position);
            }

            guardedSince = null;
            peak = profit;
        }

        peak = Math.max(peak, profit);

        if (profit < peak - allowance()) {
            guardedSince = last.getTime();
            trips++;

            return closing(book.position);
        }

        return delegate.calculate(lastCandles);
    }

    /** What the curve may fall from its high water mark: a share of the profit, or the loss budget. */
    private double allowance() {
        return Math.max(giveBack * Math.max(peak, 0), lossBudget * allocated);
    }

    /** Out of the position if there is one, and out of the market while there is not. */
    private Upside closing(long position) {
        if (position > 0) {
            return new Upside(-1, 1);
        }

        if (position < 0) {
            return new Upside(1, 1);
        }

        return Upside.NEUTRAL;
    }

    /**
     * Folds into the running book what this instrument has done since the last time it was asked.
     * Nothing hands a calculator a portfolio, and the operations are the same record the broker
     * settled against. Fees carry the uid of the instrument that incurred them, so they count here too.
     * <p>
     * What is asked for is the window since the last operation already folded in, not the whole
     * history: read from the beginning every bar, the query would grow with the run and the cost of
     * the guard with the square of it. The window is left-inclusive and the operations already taken
     * from its edge are remembered, because an execution can be recorded after the bar it happened
     * on - dropping the edge would lose it, and taking the edge twice would count it twice.
     * <p>
     * The bound is the bar being decided on, never the end of the book, so nothing that has not
     * happened yet can be read. The scheme assumes operations settle in time order, which is what a
     * broker's own record does.
     */
    private void fold(Instant until) {
        if (until.isBefore(folded)) {
            return;
        }

        List<Operation> since = operations
            .getByAccountIdAndInstrumentUid(accountId, instrumentUid, new TimeRange(folded, until))
            .stream()
            .filter(operation -> operation.state() == OperationState.EXECUTED)
            .filter(operation -> !foldedAtBoundary.contains(operation.id()))
            .sorted(Comparator.comparing(ProfitGiveBackUpsideCalculator::timeOf))
            .toList();

        for (Operation operation : since) {
            apply(operation);

            Instant at = timeOf(operation);

            if (at.isAfter(folded)) {
                folded = at;
                foldedAtBoundary.clear();
            }

            foldedAtBoundary.add(operation.id());
        }
    }

    private void apply(Operation operation) {
        double payment = Math.abs(operation.payment().toDouble());

        if (operation.direction().isBuy()) {
            book.cash -= payment;
            book.position += operation.quantityDone();
        } else if (operation.direction().isSell()) {
            book.cash += payment;
            book.position -= operation.quantityDone();
        } else {
            book.cash -= payment;
        }
    }

    /** When the operation happened, which for one that never executed is when it was created. */
    private static Instant timeOf(Operation operation) {
        return operation.executedDate() != null ? operation.executedDate() : operation.date();
    }

    private static class Book {
        private double cash;
        private long position;
    }
}
