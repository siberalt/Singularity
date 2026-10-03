package com.siberalt.singularity.broker.contract.service.margin;

/**
 * The two things a balance needs from outside to value itself: what a position is worth now, and what the
 * broker demands be covered of it.
 * <p>
 * It exists so that the margin arithmetic can live next to the money and the positions it is about, without
 * the balance having to know what a candle is or how instruments are looked up. Both answers are per
 * instrument uid, which is the only identifier a balance holds.
 * <p>
 * A price of zero means the instrument has no market right now - its data has ended, or the simulation has
 * not reached it. Such a position drops out of equity, which is the honest answer: there is no price to
 * value it at, and it could not be traded either.
 */
public interface MarginContext {
    /** No market and no credit anywhere - what an empty context answers. */
    MarginContext NONE = new MarginContext() {
        @Override
        public double priceOf(String instrumentUid) {
            return 0;
        }

        @Override
        public MarginRequirement requirementOf(String instrumentUid) {
            return MarginRequirement.CASH;
        }
    };

    /** The price one share is worth now, or zero when there is no market. */
    double priceOf(String instrumentUid);

    /** What share of this instrument's value the broker wants covered. */
    MarginRequirement requirementOf(String instrumentUid);
}
