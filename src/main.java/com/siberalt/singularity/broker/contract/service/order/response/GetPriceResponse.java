package com.siberalt.singularity.broker.contract.service.order.response;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

public record GetPriceResponse(
    Quotation totalBalanceChange,
    Quotation executedCommission
) {}
