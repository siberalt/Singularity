package com.siberalt.singularity.entity.candle.cvs;

public class CvsTestConfig {
    protected long instrumentId;
    protected String instrumentDataPath;

    public CvsTestConfig(long instrumentId, String instrumentDataPath) {
        this.instrumentId = instrumentId;
        this.instrumentDataPath = instrumentDataPath;
    }

    public long getInstrumentId() {
        return instrumentId;
    }

    public String getInstrumentDataPath() {
        return instrumentDataPath;
    }
}
