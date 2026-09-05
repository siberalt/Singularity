package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.transaction.TransactionSpec;

import java.util.List;

/**
 * What one fill of a given size would consist of and cost, before anything is applied to the
 * account. Quoting and applying are separate steps because the answer is needed on its own - to
 * price an order the client is only asking about, and to reserve funds for an order that will fill
 * later.
 *
 * @param transactionSpecs the money movements this fill consists of - the trade and its commission
 * @param balanceChange    their total, negative for a buy
 * @param commission       the commission part alone, always negative
 */
public record FillQuote(
    List<TransactionSpec> transactionSpecs,
    Quotation balanceChange,
    Quotation commission
) {
    /**
     * The cost of the fill as a positive amount - what has to be available, or reserved, to cover
     * it. Meaningful for a buy; a sell brings money in, so its cost is negative.
     */
    public Quotation cost() {
        return balanceChange.multiply(-1);
    }
}
