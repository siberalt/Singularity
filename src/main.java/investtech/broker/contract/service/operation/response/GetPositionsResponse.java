package investtech.broker.contract.service.operation.response;

import investtech.broker.contract.value.money.Money;

import java.util.Collection;

public class GetPositionsResponse {
    protected Collection<Money> money;
    protected Collection<Money> blocked;
    protected Collection<Position> securities;

    public Collection<Money> getMoney() {
        return money;
    }

    public GetPositionsResponse setMoney(Collection<Money> money) {
        this.money = money;
        return this;
    }

    public Collection<Money> getBlocked() {
        return blocked;
    }

    public GetPositionsResponse setBlocked(Collection<Money> blocked) {
        this.blocked = blocked;
        return this;
    }

    public Collection<Position> getSecurities() {
        return securities;
    }

    public GetPositionsResponse setSecurities(Collection<Position> securities) {
        this.securities = securities;
        return this;
    }

    @Override
    public GetPositionsResponse clone() {
        try {
            return (GetPositionsResponse) super.clone();
        } catch (CloneNotSupportedException exception) {
            throw new RuntimeException(exception);
        }
    }
}
