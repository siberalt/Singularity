package com.siberalt.singularity.broker.impl.mock.shared.operation;

import com.siberalt.singularity.entity.position.Position;
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

        return resultTransaction
            .setStatus(TransactionStatus.COMPLETED)
            .setExecutedTime(clock.currentTime());
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
        assertCountPositive(count);
        updatePositionBalance(instrumentUid, balance -> balance + count);
    }

    public void subtractPositionBalance(String instrumentUid, long count) {
        assertCountPositive(count);
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

    /**
     * Applies {@code updater} to the current balance of the money's currency, treating a currency
     * the account has never held as a zero balance. The updater must be applied in every case -
     * skipping it for an absent currency would turn a subtraction into a deposit.
     */
    protected void updateMoneyBalance(Map<String, Money> moneyBalance, Money money, Function<Money, Money> updater) {
        String currencyIso = money.getCurrencyIso();
        Money currentBalance = moneyBalance.getOrDefault(currencyIso, Money.of(currencyIso, Quotation.ZERO));
        Money updatedBalance = updater.apply(currentBalance);

        if (updatedBalance.getQuotation().isNegative()) {
            throw new IllegalStateException(
                String.format(
                    "Balance of account %s in %s would go negative: %s -> %s",
                    accountId,
                    currencyIso,
                    currentBalance,
                    updatedBalance
                )
            );
        }

        moneyBalance.put(currencyIso, updatedBalance);
    }

    protected void updatePositionBalance(String instrumentUid, Function<Long, Long> updater) {
        var position = positions.get(instrumentUid);

        if (position == null) {
            throw new IllegalStateException(
                String.format("Account %s has no position in instrument %s", accountId, instrumentUid)
            );
        }

        position.setBalance(updater.apply(position.getBalance()));
    }

    private void assertMoneyPositive(Money money) {
        if (!money.getQuotation().isGreaterThan(Quotation.ZERO)) {
            throw new IllegalArgumentException(String.format("Money value should be positive, got %s", money));
        }
    }

    private void assertCountPositive(long count) {
        if (count <= 0) {
            throw new IllegalArgumentException(String.format("Count should be positive, got %d", count));
        }
    }
}
