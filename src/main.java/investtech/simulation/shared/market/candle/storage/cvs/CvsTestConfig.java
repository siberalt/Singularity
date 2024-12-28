package investtech.simulation.shared.market.candle.storage.cvs;

public class CvsTestConfig {
    protected String instrumentUid;
    protected String instrumentDataPath;

    public CvsTestConfig(String instrumentUid, String instrumentDataPath) {
        this.instrumentUid = instrumentUid;
        this.instrumentDataPath = instrumentDataPath;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public String getInstrumentDataPath() {
        return instrumentDataPath;
    }
}
