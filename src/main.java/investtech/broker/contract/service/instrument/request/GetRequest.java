package investtech.broker.contract.service.instrument.request;

public class GetRequest {
    protected String id;

    public String getId() {
        return id;
    }

    public GetRequest setId(String id) {
        this.id = id;
        return this;
    }

    public static GetRequest of(String id) {
        return new GetRequest().setId(id);
    }
}
