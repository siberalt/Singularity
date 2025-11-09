package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;

public interface PriceExtractor {
    Quotation extract(Candle candle);
}
