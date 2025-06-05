package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.exception.ExceptionBuilder;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.operation.OperationsService;
import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.contract.service.operation.response.GetPositionsResponse;
import com.siberalt.singularity.broker.contract.service.operation.response.Position;
import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.impl.mock.shared.operation.AccountBalance;
import com.siberalt.singularity.broker.impl.mock.shared.operation.OpenPosition;

import java.util.*;

public class MockOperationsService implements OperationsService {
    private final Map<String, AccountBalance> accountBalances = new HashMap<>();
    private final MockBroker mockBroker;

    public MockOperationsService(MockBroker virtualBroker) {
        this.mockBroker = virtualBroker;
    }

    @Override
    public GetPositionsResponse getPositions(GetPositionsRequest request) throws AbstractException {
        checkAccountExists(request.getAccountId());

        return getOrCreateBalance(request.getAccountId()).toResponse();
    }

    public Position getPositionByInstrumentId(String accountId, String instrumentUid) throws AbstractException {
        checkAccountExists(accountId);

        return getOrCreateBalance(accountId).getPositionByInstrumentId(instrumentUid);
    }

    public boolean isEnoughOfMoney(String accountId, Money amount) throws AbstractException {
        checkAccountExists(accountId);

        return getOrCreateBalance(accountId).isEnoughOfMoney(amount);
    }

    public List<Money> getAvailableMoney(String accountId) throws AbstractException {
        checkAccountExists(accountId);

        return getOrCreateBalance(accountId).getAvailableMoney();
    }

    public Money getAvailableMoney(String accountId, String currencyIso) throws AbstractException {
        checkAccountExists(accountId);

        return getOrCreateBalance(accountId).getAvailableMoney(currencyIso);
    }

    public void addMoney(String accountId, Money money) throws AbstractException {
        checkAccountExists(accountId);
        getOrCreateBalance(accountId).addAvailableMoney(money);
    }

    public void subtractMoney(String accountId, Money money) throws AbstractException {
        checkAccountExists(accountId);
        getOrCreateBalance(accountId).subtractAvailableMoney(money);
    }

    public void blockMoney(String accountId, Money money) throws AbstractException {
        checkAccountExists(accountId);
        AccountBalance accountBalance = getOrCreateBalance(accountId);

        if (!accountBalance.isEnoughOfMoney(money)) {
            throw ExceptionBuilder.create(ErrorCode.INSUFFICIENT_BALANCE);
        }

        accountBalance.addBlockedMoney(money);
        accountBalance.subtractAvailableMoney(money);
    }

    public void unblockMoney(String accountId, Money money) throws AbstractException {
        checkAccountExists(accountId);
        var accountBalance = getOrCreateBalance(accountId);

        accountBalance.subtractBlockedMoney(money);
        accountBalance.addAvailableMoney(money);
    }

    public void openPosition(String accountId, OpenPosition createPosition) throws AbstractException {
        checkAccountExists(accountId);
        getOrCreateBalance(accountId).addPosition(
                new Position()
                        .setBlocked(createPosition.getInitialBlocked())
                        .setBalance(createPosition.getInitialBalance())
                        .setInstrumentType(createPosition.getInstrumentType())
                        .setExchangeBlocked(createPosition.isExchangeBlocked())
                        .setInstrumentUid(Objects.requireNonNull(createPosition.getInstrumentId()))
                        .setPositionUid(
                                Objects.requireNonNullElse(createPosition.getPositionId(), UUID.randomUUID().toString())
                        )
        );
    }

    public void closePosition(String accountId, String instrumentUid) throws AbstractException {
        checkAccountExists(accountId);

        if (!accountBalances.containsKey(accountId)) {
            throw ExceptionBuilder.create(ErrorCode.POSITION_NOT_FOUND);
        }

        accountBalances.get(accountId).removePosition(instrumentUid);
    }

    public void subtractFromPosition(String accountId, String instrumentUid, long count) throws AbstractException {
        checkAccountExists(accountId);
        var accountBalance = getOrCreateBalance(accountId);

        if (!accountBalance.hasPositionByInstrumentUid(instrumentUid)) {
            var instrument = mockBroker
                    .instrumentService
                    .get(GetRequest.of(instrumentUid))
                    .getInstrument();
            var newPosition = new Position()
                    .setBalance(0)
                    .setPositionUid(UUID.randomUUID().toString())
                    .setBlocked(0)
                    .setInstrumentUid(instrumentUid)
                    .setInstrumentType(instrument.getInstrumentType().name());
            accountBalance.addPosition(newPosition);
        }

        accountBalance.subtractPositionBalance(instrumentUid, count);
    }

    public void addToPosition(String accountId, String instrumentUid, long count) throws AbstractException {
        checkAccountExists(accountId);
        var accountBalance = getOrCreateBalance(accountId);

        if (!accountBalance.hasPositionByInstrumentUid(instrumentUid)) {
            var instrument = mockBroker
                    .instrumentService
                    .get(GetRequest.of(instrumentUid))
                    .getInstrument();
            var newPosition = new Position()
                    .setBalance(0)
                    .setPositionUid(UUID.randomUUID().toString())
                    .setBlocked(0)
                    .setInstrumentUid(instrumentUid)
                    .setInstrumentType(instrument.getInstrumentType().name());
            accountBalance.addPosition(newPosition);
        }

        accountBalance.addPositionBalance(instrumentUid, count);
    }

    private AccountBalance getOrCreateBalance(String accountId) {
        if (!accountBalances.containsKey(accountId)) {
            accountBalances.put(accountId, new AccountBalance(accountId));
        }

        return accountBalances.get(accountId);
    }

    private void checkAccountExists(String accountId) throws AbstractException {
        if (!mockBroker.getUserService().accountExists(accountId)) {
            throw ExceptionBuilder.create(ErrorCode.ACCOUNT_NOT_FOUND);
        }
    }
}
