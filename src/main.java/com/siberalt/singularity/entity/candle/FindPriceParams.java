package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.time.Instant;

public record FindPriceParams(
    String instrumentUid,
    Instant from,
    Instant to,
    Quotation price,
    ComparisonOperator comparisonOperator,
    int maxCount
) {}
