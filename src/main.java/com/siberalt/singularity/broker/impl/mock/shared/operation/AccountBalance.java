package com.siberalt.singularity.broker.impl.mock.shared.operation;

import com.siberalt.singularity.broker.contract.service.operation.response.GetPositionsResponse;
import com.siberalt.singularity.broker.contract.service.operation.response.Position;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class AccountBalance {
    protected String accountId;
    protected Map<String, Money> availableMonies = new HashMap<>();
    protected Map<String, Money> blockedMonies = new HashMap<>();
    protected Map<String, Position> positions = new HashMap<>();

    public AccountBalance(String accountId) {
        this.accountId = accountId;
    }

    public GetPositionsResponse toResponse() {
        return new GetPositionsResponse()
                .setSecurities(positions.values())
                .setMoney(availableMonies.values())
                .setBlocked(blockedMonies.values());
    }

    public String getAccountId() {
        return accountId;
    }

    public boolean isEnoughOfMoney(Money amount) {
        return availableMonies.containsKey(amount.getCurrencyIso())
                && availableMonies.get(amount.getCurrencyIso()).isMoreThanOrEqual(amount);
    }

    public Position getPositionByInstrumentId(String instrumentId) {
        return positions.get(instrumentId);
    }

    public boolean hasPositionByInstrumentUid(String instrumentUid) {
        return positions.containsKey(instrumentUid);
    }

    public void addAvailableMoney(Money money) {
        assertMoneyPositive(money);
        updateMoneyBalance(availableMonies, money, balance -> balance.add(money));
    }

    public void subtractAvailableMoney(Money money) {
        assertMoneyPositive(money);
        updateMoneyBalance(availableMonies, money, balance -> balance.subtract(money));
    }

    public void addBlockedMoney(Money money) {
        assertMoneyPositive(money);
        updateMoneyBalance(blockedMonies, money, balance -> balance.add(money));
    }

    public void subtractBlockedMoney(Money money) {
        assertMoneyPositive(money);
        updateMoneyBalance(blockedMonies, money, balance -> balance.subtract(money));
    }

    public void addPosition(Position security) {
        positions.put(security.getInstrumentUid(), security);
    }

    public void removePosition(String instrumentUid) {
        positions.remove(instrumentUid);
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
        return availableMonies.getOrDefault(currencyIso, Money.of(currencyIso, Quotation.ZERO));
    }

    public List<Money> getAvailableMoney() {
        return availableMonies.values().stream().toList();
    }

    protected void updateMoneyBalance(Map<String, Money> moneyBalance, Money money, Function<Money, Money> updater) {
        money = moneyBalance.containsKey(money.getCurrencyIso())
                ? updater.apply(moneyBalance.get(money.getCurrencyIso()))
                : money;
        moneyBalance.put(money.getCurrencyIso(), money);
    }

    protected void updatePositionBalance(String instrumentUid, Function<Long, Long> updater) {
        var position = positions.get(instrumentUid);
        position.setBalance(updater.apply(position.getBalance()));
    }

    private void assertMoneyPositive(Money money) {
        assert money.getQuotation().isMore(Quotation.of(0D)) : "Money value should be positive";
    }
}
