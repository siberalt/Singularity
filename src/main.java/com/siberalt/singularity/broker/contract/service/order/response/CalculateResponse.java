package com.siberalt.singularity.broker.contract.service.order.response;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.transaction.TransactionSpec;

import java.util.List;

public record CalculateResponse(
    String instrumentUid,
    Quotation totalBalanceChange,
    long quantity,
    List<TransactionSpec> transactionTemplates
) {}
