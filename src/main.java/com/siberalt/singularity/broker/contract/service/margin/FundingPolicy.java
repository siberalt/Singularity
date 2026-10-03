package com.siberalt.singularity.broker.contract.service.margin;

import com.siberalt.singularity.broker.contract.value.money.Money;

import java.time.Instant;

/**
 * What borrowed money and borrowed shares cost while they are held.
 * <p>
 * The piece of margin trading a backtest forgets, and the one that decides whether a slow short pays. A
 * commission is charged twice and is over; funding is charged every day a position stands, so a rule
 * holding a short for a month pays thirty times what a rule holding it for a day pays, and nothing in a
 * per-trade excess figure reflects that.
 * <p>
 * Two things are charged by the same contract because they are the same thing from the broker's side:
 * money lent to a long on margin, and shares lent to a short. Both are a loan outstanding over an
 * interval, and both are quoted as a rate on it.
 * <p>
 * The amount passed in is what is <b>borrowed</b> and is positive. The returned charge is negative, like
 * every other cost in this contract.
 */
public interface FundingPolicy {
    /** Nothing charged - for measuring the mechanics without the price of them. */
    FundingPolicy FREE = (borrowed, from, to) -> {
        checkLoan(borrowed, from, to);

        return Money.of(borrowed.getCurrencyIso(), 0.0);
    };

    /**
     * @param borrowed how much is on loan, positive, in the account's currency
     * @param from     when the loan started being counted
     * @param to       when it stopped, at or after {@code from}
     * @return the cost of that loan over that interval, negative; zero over no time at all
     * @throws IllegalArgumentException on a missing amount or instant, or an interval that runs backwards
     */
    Money over(Money borrowed, Instant from, Instant to);

    /**
     * What every implementation has to reject, kept in one place.
     * <p>
     * These are mistakes in the caller, not states of the market: a loan with no amount, a loan with no
     * dates, a loan repaid before it was taken. Returning zero for them - which this contract did at first
     * - makes a broken accrual look like a free one, and a simulation that silently charges nothing for its
     * shorts is worse than one that stops. An interval of zero length is different and is allowed: a
     * position opened and closed in the same instant really does cost no funding.
     */
    static void checkLoan(Money borrowed, Instant from, Instant to) {
        if (borrowed == null || from == null || to == null) {
            throw new IllegalArgumentException("Нужны сумма займа и его границы");
        }

        if (to.isBefore(from)) {
            throw new IllegalArgumentException("Заём не может кончиться раньше, чем начался: "
                + from + " .. " + to);
        }
    }
}
