package investtech.broker.contract.service.instrument;

import investtech.broker.contract.service.instrument.common.InstrumentType;

import java.io.*;

public class Instrument {
    protected String name;
    protected String uid;
    protected String positionUid;
    protected InstrumentType instrumentType;

    public String getName() {
        return name;
    }

    public Instrument setName(String name) {
        this.name = name;
        return this;
    }

    public String getUid() {
        return uid;
    }

    public Instrument setUid(String uid) {
        this.uid = uid;
        return this;
    }

    public String getPositionUid() {
        return positionUid;
    }

    public Instrument setPositionUid(String positionUid) {
        this.positionUid = positionUid;
        return this;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public Instrument setInstrumentType(InstrumentType instrumentType) {
        this.instrumentType = instrumentType;
        return this;
    }

    @Override
    public String toString() {
        return String.join("",
                String.format("name: %s\n", getName()),
                String.format("instrumentType: %s\n", getInstrumentType()),
                String.format("uid: %s\n", getUid()),
                String.format("positionUid: %s\n", getPositionUid())
        );
    }
}
