package investtech.broker.contract.service.market.request;

import java.util.List;

public class GetLastPricesRequest {
    Iterable<String> instrumentsUid;

    public Iterable<String> getInstrumentsUid() {
        return instrumentsUid;
    }

    public GetLastPricesRequest setInstrumentsUid(Iterable<String> instrumentsUid) {
        this.instrumentsUid = instrumentsUid;
        return this;
    }

    public static GetLastPricesRequest of(Iterable<String> instrumentsUid) {
        return new GetLastPricesRequest().setInstrumentsUid(instrumentsUid);
    }

    public static GetLastPricesRequest of(String instrumentUid) {
        return of(List.of(instrumentUid));
    }
}
