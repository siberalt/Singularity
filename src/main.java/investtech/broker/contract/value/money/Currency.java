package investtech.broker.contract.value.money;

public enum Currency {
    USD,
    RUB;

    public String getIsoCode() {
        return this.name();
    }
}
