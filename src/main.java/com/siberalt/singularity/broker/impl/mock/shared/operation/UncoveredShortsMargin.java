package com.siberalt.singularity.broker.impl.mock.shared.operation;

import com.siberalt.singularity.broker.contract.service.margin.MarginContext;

/**
 * Shorts allowed for nothing: a sale is never refused, a purchase is still paid for in full.
 * <p>
 * This is not a tariff anybody offers - it is the fixture the mock broker's old {@code shortsAllowed} flag
 * amounted to, kept because five years of measurements in {@code docs/signals.md} were taken through it and
 * have to stay reproducible. The flag switched the sell-side check off and put nothing in its place; this
 * says the same thing in the one place that now decides cover, instead of as a boolean two classes deep.
 * <p>
 * What it misrepresents, in case a new measurement is tempted to use it: there is no margin requirement, no
 * borrow fee, no recall of the borrowed lots and no check that the instrument can be borrowed at all. A
 * strategy left short for months pays nothing for the privilege, while a real one would. For a short of an
 * hour the borrow is a fraction of a basis point and this stays close enough to the truth to measure; for
 * anything longer, use a real {@link com.siberalt.singularity.broker.contract.service.margin.MarginPolicy}
 * and charge funding.
 */
public class UncoveredShortsMargin extends Margin {
    public UncoveredShortsMargin(AccountBalance balance, MarginContext context) {
        super(balance, context, false);
    }

    @Override
    public boolean covers(String instrumentUid, String currencyIso, long shares, double price,
                          double commission) {
        return shares < 0 || super.covers(instrumentUid, currencyIso, shares, price, commission);
    }
}
