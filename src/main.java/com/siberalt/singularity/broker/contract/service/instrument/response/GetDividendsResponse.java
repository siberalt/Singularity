package com.siberalt.singularity.broker.contract.service.instrument.response;

import com.siberalt.singularity.broker.contract.service.instrument.common.Dividend;

import java.util.List;

public class GetDividendsResponse {
    protected List<Dividend> dividends = List.of();

    public List<Dividend> getDividends() {
        return dividends;
    }

    public GetDividendsResponse setDividends(List<Dividend> dividends) {
        this.dividends = dividends;
        return this;
    }
}
