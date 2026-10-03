package com.siberalt.singularity.broker.contract.service.margin;

import com.siberalt.singularity.entity.instrument.Instrument;

/**
 * What the broker demands as cover, instrument by instrument.
 * <p>
 * Kept as a contract rather than a number on the instrument entity for two reasons. The rates are the
 * broker's, not the share's - two brokers lend differently against the same paper, and the same broker
 * lends differently to different clients ({@code dlong_client} in the T-Invest API). And they change: a
 * share can be struck off the marginal list overnight, which is a fact about today's policy and not about
 * the instrument we store history for.
 * <p>
 * A policy that does not know an instrument should say so by returning {@link MarginRequirement#CASH}
 * rather than guessing a rate: full cover is the safe answer, it refuses leverage instead of inventing it,
 * and a short becomes impossible rather than silently free.
 */
public interface MarginPolicy {
    /** No leverage for anything - the behaviour of an account that cannot borrow. */
    MarginPolicy CASH_ONLY = instrument -> MarginRequirement.CASH;

    MarginRequirement of(Instrument instrument);
}
