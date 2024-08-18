package investtech.broker.contract.service.instrument.request;


import investtech.broker.contract.service.instrument.common.InstrumentType;

public class GetRequest {
    protected String id;

    protected InstrumentType instrumentType;

    public String getId() {
        return id;
    }

    public GetRequest setId(String id) {
        this.id = id;
        return this;
    }

    public InstrumentType getInstrumentType() {
        return instrumentType;
    }

    public GetRequest setInstrumentType(InstrumentType instrumentType) {
        this.instrumentType = instrumentType;
        return this;
    }

    public static GetRequest of(String id, InstrumentType instrumentType) {
        return new GetRequest()
                .setId(id)
                .setInstrumentType(instrumentType);
    }
}
