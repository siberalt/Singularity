package com.siberalt.singularity.broker.contract.service.margin;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Duration;
import java.time.Instant;

/**
 * Funding at the money market's own rate plus a spread, read off a {@link RateCurve}.
 * <p>
 * A fixed annual percentage cannot describe 2021-2026 in Russia: the key rate went 4.25% to 20% to 7.5% to
 * 21%, so any single number is wrong by a factor of four at one end of the window or the other. A curve of
 * money market levels is that rate, realised and already compounded, and the cheapest honest one is a money
 * market fund - the same LQDT series the studies park their idle cash in.
 * <p>
 * The cost of a loan over an interval is therefore what the curve grew by over exactly that interval, plus
 * the broker's spread accrued on an actual/365 basis. Taking the growth rather than an annualised rate is
 * deliberate: it needs no day count, it is right across weekends and holidays without special cases, and it
 * matches what the lender's own money would have earned instead.
 */
public class CurveFunding implements FundingPolicy {
    private static final double YEAR = 365.0;

    private final RateCurve curve;
    private final double spread;

    /**
     * @param curve  the level of the money market over time - its growth is the rate
     * @param spread what the broker adds on top, as an annual share: 0.03 for three points over the market
     */
    public CurveFunding(RateCurve curve, double spread) {
        if (curve == null) {
            throw new IllegalArgumentException("Нужна кривая ставки");
        }

        if (spread < 0) {
            throw new IllegalArgumentException("Спред не может быть отрицательным: " + spread);
        }

        this.curve = curve;
        this.spread = spread;
    }

    @Override
    public Money over(Money borrowed, Instant from, Instant to) {
        FundingPolicy.checkLoan(borrowed, from, to);

        if (from.equals(to)) {
            return Money.of(borrowed.getCurrencyIso(), 0.0);
        }

        double level = curve.at(from);
        double grown = level <= 0 ? 1 : curve.at(to) / level;
        double days = (double) Duration.between(from, to).toMillis() / Duration.ofDays(1).toMillis();
        double rate = grown - 1 + spread * days / YEAR;

        return Money.of(borrowed.getCurrencyIso(),
            Quotation.of(borrowed.getQuotation().toDouble() * rate).multiply(-1));
    }
}
