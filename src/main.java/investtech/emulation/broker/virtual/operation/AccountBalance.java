package investtech.emulation.broker.virtual.operation;

import investtech.broker.contract.service.operation.response.GetPositionsResponse;
import investtech.broker.contract.service.operation.response.PositionSecurities;
import investtech.broker.contract.value.money.Money;

import java.util.Map;

public class AccountBalance {
    interface BalanceUpdaterInterface<BalanceT> {
        BalanceT update(BalanceT balance);
    }

    protected Map<String, Money> availableMonies;
    protected Map<String, Money> blockedMonies;
    protected Map<String, PositionSecurities> positions;

    public GetPositionsResponse toResponse() {
        return new GetPositionsResponse()
                .setSecurities(positions.values())
                .setMoney(availableMonies.values())
                .setBlocked(blockedMonies.values());
    }

    public boolean isEnoughOfMoney(Money amount) {
        return availableMonies.containsKey(amount.getCurrencyIso())
                && availableMonies.get(amount.getCurrencyIso()).isMoreThanOrEqual(amount);
    }

    public boolean hasPositionByInstrumentUid(String instrumentUid) {
        return positions.containsKey(instrumentUid);
    }

    public void addAvailableMoney(Money money) {
        assert money.isMoreThan(money): "Money value should be positive";
        updateMoneyBalance(availableMonies, money, balance -> balance.add(money));
    }

    public void subtractAvailableMoney(Money money) {
        assert money.isMoreThan(money): "Money value should be positive";
        updateMoneyBalance(availableMonies, money, balance -> balance.subtract(money));
    }

    public void addBlockedMoney(Money money) {
        assert money.isMoreThan(money): "Money value should be positive";
        updateMoneyBalance(blockedMonies, money, balance -> balance.add(money));
    }

    public void subtractBlockedMoney(Money money) {
        assert money.isMoreThan(money): "Money value should be positive";
        updateMoneyBalance(blockedMonies, money, balance -> balance.subtract(money));
    }

    public void addPosition(PositionSecurities security) {
        positions.put(security.getInstrumentUid(), security);
    }

    public void addPositionBalance(String instrumentUid, long count) {
        assert count > 0 : "Count should be positive";
        updatePositionBalance(instrumentUid, balance -> balance + count);
    }

    public void subtractPositionBalance(String instrumentUid, long count) {
        assert count > 0 : "Count should be positive";
        updatePositionBalance(instrumentUid, balance -> balance - count);
    }

    public Money getBlockedMoney(String currencyIso) {
        return blockedMonies.getOrDefault(currencyIso, null);
    }

    public Money getAvailableMoney(String currencyIso) {
        return availableMonies.getOrDefault(currencyIso, null);
    }

    protected void updateMoneyBalance(Map<String, Money> moneyBalance, Money money, BalanceUpdaterInterface<Money> updater) {
        money = moneyBalance.containsKey(money.getCurrencyIso())
                ? updater.update(moneyBalance.get(money.getCurrencyIso()))
                : money;
        moneyBalance.put(money.getCurrencyIso(), money);
    }

    protected void updatePositionBalance(String instrumentUid, BalanceUpdaterInterface<Long> updater) {
        var position = positions.get(instrumentUid);
        position.setBalance(updater.update(position.getBalance()));
    }
}
