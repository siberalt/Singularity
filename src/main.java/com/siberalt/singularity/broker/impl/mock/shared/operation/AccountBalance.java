package com.siberalt.singularity.broker.impl.mock.shared.operation;

import com.siberalt.singularity.broker.contract.service.operation.response.Position;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.transaction.Transaction;
import com.siberalt.singularity.entity.transaction.TransactionStatus;
import com.siberalt.singularity.entity.transaction.TransactionSpec;
import com.siberalt.singularity.strategy.context.Clock;

import java.util.*;
import java.util.function.Function;

public class AccountBalance {
    private final String accountId;
    private final String brokerId;
    private final Clock clock;
    protected Map<String, Money> availableMonies = new HashMap<>();
    protected Map<String, Money> blockedMonies = new HashMap<>();
    protected Map<String, Position> positions = new HashMap<>();

    public AccountBalance(String accountId, Clock clock, String brokerId) {
        this.accountId = accountId;
        this.clock = clock;
        this.brokerId = brokerId;
    }

    public String getAccountId() {
        return accountId;
    }

    public Collection<Money> getBlockedMonies() {
        return blockedMonies.values();
    }

    public Collection<Position> getPositions() {
        return positions.values();
    }

    public List<Transaction> applyTransactions(List<TransactionSpec> transactions) {
        List<Transaction> result = new ArrayList<>();
        for (TransactionSpec transaction : transactions) {
            Transaction resultTransaction = applyTransaction(transaction);
            result.add(resultTransaction);
            if (resultTransaction.getStatus() == TransactionStatus.FAILED) {
                // If any transaction fails, we stop processing further transactions
                return result;
            }
        }
        return result;
    }

    public Transaction applyTransaction(TransactionSpec transaction) {
        Transaction resultTransaction = new Transaction()
            .setId(UUID.randomUUID().toString())
            .setDescription(transaction.description())
            .setType(transaction.type())
            .setAmount(transaction.amount())
            .setDestinationAccountId(accountId)
            .setSourceAccountId(brokerId)
            .setCreatedTime(clock.currentTime())
            .setStatus(TransactionStatus.PENDING);

        try {
            updateMoneyBalance(availableMonies, transaction.amount(), balance -> balance.add(transaction.amount()));
        } catch (Exception e) {
            resultTransaction.setStatus(TransactionStatus.FAILED);
            resultTransaction.setErrorMessage(e.getMessage());
            return resultTransaction;
        }

        return resultTransaction;
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
        assert money.getQuotation().isGreaterThan(Quotation.of(0D)) : "Money value should be positive";
    }
}
