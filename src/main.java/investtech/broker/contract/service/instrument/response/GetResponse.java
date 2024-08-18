package investtech.broker.contract.service.instrument.response;

import investtech.broker.contract.service.instrument.Instrument;

public class GetResponse {
    protected Instrument instrument;

    public Instrument getInstrument() {
        return instrument;
    }

    public GetResponse setInstrument(Instrument instrument) {
        this.instrument = instrument;
        return this;
    }
}
