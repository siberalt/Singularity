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

    /**
     * All-or-nothing: the whole batch is checked against the balance before any of it is applied,
     * so a batch that would overdraw leaves the account exactly as it was. Applying transaction by
     * transaction would strand the earlier ones of a failed fill - the money moved but the position
     * never changed.
     *
     * @return one {@link Transaction} per spec, all COMPLETED, or - if the batch was refused - the
     *         transactions up to and including the FAILED one, none of which were applied.
     */
    public List<Transaction> applyTransactions(List<TransactionSpec> transactions) {
        List<Transaction> result = new ArrayList<>();
        Map<String, Money> projectedBalances = new HashMap<>();

        for (TransactionSpec transaction : transactions) {
            String currencyIso = transaction.amount().getCurrencyIso();
            Money projected = projectedBalances
                .computeIfAbsent(currencyIso, this::getAvailableMoney)
                .add(transaction.amount());

            if (projected.getQuotation().isNegative()) {
                result.add(
                    toTransaction(transaction)
                        .setStatus(TransactionStatus.FAILED)
                        .setErrorMessage(
                            String.format(
                                "Balance of account %s in %s would go negative: %s -> %s",
                                accountId,
                                currencyIso,
                                projectedBalances.get(currencyIso),
                                projected
                            )
                        )
                );

                return result;
            }

            projectedBalances.put(currencyIso, projected);
            result.add(toTransaction(transaction));
        }

        availableMonies.putAll(projectedBalances);
        result.forEach(
            transaction -> transaction
                .setStatus(TransactionStatus.COMPLETED)
                .setExecutedTime(clock.currentTime())
        );

        return result;
    }

    public Transaction applyTransaction(TransactionSpec transaction) {
        return applyTransactions(List.of(transaction)).getFirst();
    }

    protected Transaction toTransaction(TransactionSpec spec) {
        return new Transaction()
            .setId(UUID.randomUUID().toString())
            .setDescription(spec.description())
            .setType(spec.type())
            .setAmount(spec.amount())
            .setDestinationAccountId(accountId)
            .setSourceAccountId(brokerId)
            .setCreatedTime(clock.currentTime())
            .setStatus(TransactionStatus.PENDING);
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

    /**
     * Moves lots out of the free balance and into the blocked part, reserving them for an order
     * that is waiting to fill. The mirror of {@link #addBlockedMoney} for instruments: everything
     * that asks "can this account sell?" looks at the free balance, so reserved lots cannot be
     * promised twice.
     */
    public void blockPosition(String instrumentUid, long count) {
        assertCountPositive(count);

        Position position = requirePosition(instrumentUid);

        if (position.getBalance() < count) {
            throw new IllegalStateException(
                String.format(
                    "Account %s has %d lots of %s free, cannot block %d",
                    accountId,
                    position.getBalance(),
                    instrumentUid,
                    count
                )
            );
        }

        position
            .setBalance(position.getBalance() - count)
            .setBlocked(position.getBlocked() + count);
    }

    public void unblockPosition(String instrumentUid, long count) {
        assertCountPositive(count);

        Position position = requirePosition(instrumentUid);

        if (position.getBlocked() < count) {
            throw new IllegalStateException(
                String.format(
                    "Account %s has %d lots of %s blocked, cannot unblock %d",
                    accountId,
                    position.getBlocked(),
                    instrumentUid,
                    count
                )
            );
        }

        position
            .setBlocked(position.getBlocked() - count)
            .setBalance(position.getBalance() + count);
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
        Position position = requirePosition(instrumentUid);
        position.setBalance(updater.apply(position.getBalance()));
    }

    protected Position requirePosition(String instrumentUid) {
        Position position = positions.get(instrumentUid);

        if (position == null) {
            throw new IllegalStateException(
                String.format("Account %s has no position in instrument %s", accountId, instrumentUid)
            );
        }

        return position;
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
