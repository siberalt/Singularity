package com.siberalt.singularity.broker.contract.service.margin;

import com.siberalt.singularity.entity.instrument.Instrument;

import java.util.HashMap;
import java.util.Map;

/**
 * Rates looked up by the broker's instrument uid, with a fallback for everything else.
 * <p>
 * This is what the T-Invest rates become once fetched: a table. The fallback matters more than it looks -
 * an instrument missing from the table is not an instrument with average risk, it is one we know nothing
 * about, so the default is {@link MarginRequirement#CASH} and a study that wants leverage on an unknown
 * instrument has to say so out loud.
 */
public class TabulatedMarginPolicy implements MarginPolicy {
    private final Map<String, MarginRequirement> byUid;
    private final MarginRequirement fallback;

    public TabulatedMarginPolicy(Map<String, MarginRequirement> byUid, MarginRequirement fallback) {
        if (byUid == null || fallback == null) {
            throw new IllegalArgumentException("Нужны таблица ставок и значение по умолчанию");
        }

        this.byUid = new HashMap<>(byUid);
        this.fallback = fallback;
    }

    public TabulatedMarginPolicy(Map<String, MarginRequirement> byUid) {
        this(byUid, MarginRequirement.CASH);
    }

    @Override
    public MarginRequirement of(Instrument instrument) {
        return instrument == null || instrument.getUid() == null ? fallback
            : byUid.getOrDefault(instrument.getUid(), fallback);
    }

    public int size() {
        return byUid.size();
    }
}
