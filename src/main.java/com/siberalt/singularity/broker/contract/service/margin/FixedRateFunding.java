package com.siberalt.singularity.broker.contract.service.margin;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Duration;
import java.time.Instant;

/**
 * One annual rate, accrued actual/365 - for a test that needs a number it can check by hand, and for a
 * window short enough that the rate did not move.
 * <p>
 * Over anything longer than a few months on this market it is the wrong tool; {@link CurveFunding} exists
 * because the rate in 2021 and the rate in 2025 differ fivefold.
 */
public class FixedRateFunding implements FundingPolicy {
    private static final double YEAR = 365.0;

    private final double annualRate;

    public FixedRateFunding(double annualRate) {
        if (annualRate < 0) {
            throw new IllegalArgumentException("Ставка не может быть отрицательной: " + annualRate);
        }

        this.annualRate = annualRate;
    }

    @Override
    public Money over(Money borrowed, Instant from, Instant to) {
        FundingPolicy.checkLoan(borrowed, from, to);

        double days = (double) Duration.between(from, to).toMillis() / Duration.ofDays(1).toMillis();
        double charge = borrowed.getQuotation().toDouble() * annualRate * days / YEAR;

        return Money.of(borrowed.getCurrencyIso(), Quotation.of(charge).multiply(-1));
    }
}
