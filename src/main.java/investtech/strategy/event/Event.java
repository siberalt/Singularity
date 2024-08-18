package investtech.strategy.event;

import investtech.shared.IdentifiableInterface;

public class Event implements IdentifiableInterface {
    protected String id;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }
}
