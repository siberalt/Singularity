package investtech.emulation.broker.virtual;

import investtech.broker.contract.service.exception.AbstractException;
import investtech.broker.contract.service.exception.ErrorCode;
import investtech.broker.contract.service.exception.ExceptionBuilder;
import investtech.broker.contract.service.instrument.request.GetRequest;
import investtech.broker.contract.service.operation.OperationsServiceInterface;
import investtech.broker.contract.service.operation.request.GetPositionsRequest;
import investtech.broker.contract.service.operation.response.GetPositionsResponse;
import investtech.broker.contract.service.operation.response.PositionSecurities;
import investtech.broker.contract.value.money.Money;
import investtech.emulation.broker.virtual.operation.AccountBalance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VirtualOperationsService implements OperationsServiceInterface {
    private final Map<String, AccountBalance> accountBalances = new HashMap<>();

    private final VirtualBroker virtualBroker;

    public VirtualOperationsService(VirtualBroker virtualBroker) {
        this.virtualBroker = virtualBroker;
    }

    @Override
    public GetPositionsResponse getPositions(GetPositionsRequest request) throws AbstractException {
        if (!accountBalances.containsKey(request.getAccountId())) {
            throw ExceptionBuilder.create(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        return accountBalances.get(request.getAccountId()).toResponse();
    }

    protected boolean isEnoughOfMoney(String accountId, Money amount) {
        assertAccountExists(accountId);

        return accountBalances.get(accountId).isEnoughOfMoney(amount);
    }

    protected VirtualOperationsService addAccountBalance(String accountId, AccountBalance accountBalance) {
        accountBalances.put(accountId, accountBalance);

        return this;
    }

    protected void addMoney(String accountId, Money money) {
        assertAccountExists(accountId);

        accountBalances.get(accountId).addAvailableMoney(money);
    }

    protected void subtractMoney(String accountId, Money money) {
        assertAccountExists(accountId);

        accountBalances.get(accountId).subtractAvailableMoney(money);
    }

    protected void blockMoney(String accountId, Money money) {
        assertAccountExists(accountId);

        if (!accountBalances.get(accountId).isEnoughOfMoney(money)) {
            // TODO throw exception
        }

        var accountBalance = accountBalances.get(accountId);
        accountBalance.addBlockedMoney(money);
        accountBalance.subtractAvailableMoney(money);
    }

    protected void unblockMoney(String accountId, Money money) {
        assertAccountExists(accountId);

        var accountBalance = accountBalances.get(accountId);
        accountBalance.subtractBlockedMoney(money);
        accountBalance.addAvailableMoney(money);
    }

    protected void addPosition(String accountId, PositionSecurities securities) {
        assertAccountExists(accountId);
        accountBalances.get(accountId).addPosition(securities);
    }

    protected void subtractPositionBalance(String accountId, String instrumentUid, long count) throws AbstractException {
        assertAccountExists(accountId);
        var accountBalance = accountBalances.get(accountId);

        if (!accountBalance.hasPositionByInstrumentUid(instrumentUid)) {
            var instrument = virtualBroker
                    .instrumentService
                    .get(GetRequest.of(instrumentUid))
                    .getInstrument();
            var newPosition = new PositionSecurities()
                    .setBalance(0)
                    .setPositionUid(UUID.randomUUID().toString())
                    .setBlocked(0)
                    .setInstrumentUid(instrumentUid)
                    .setInstrumentType(instrument.getInstrumentType().name());
            accountBalance.addPosition(newPosition);
        }

        accountBalance.addPositionBalance(instrumentUid, count);
    }

    protected void addPositionBalance(String accountId, String instrumentUid, long count) throws AbstractException {
        assertAccountExists(accountId);
        var accountBalance = accountBalances.get(accountId);

        if (!accountBalance.hasPositionByInstrumentUid(instrumentUid)) {
            var instrument = virtualBroker
                    .instrumentService
                    .get(GetRequest.of(instrumentUid))
                    .getInstrument();
            var newPosition = new PositionSecurities()
                    .setBalance(0)
                    .setPositionUid(UUID.randomUUID().toString())
                    .setBlocked(0)
                    .setInstrumentUid(instrumentUid)
                    .setInstrumentType(instrument.getInstrumentType().name());
            accountBalance.addPosition(newPosition);
        }

        accountBalance.addPositionBalance(instrumentUid, count);
    }

    private void assertAccountExists(String accountId) {
        assert accountBalances.containsKey(accountId) : String.format("Account - %s not found", accountId);
    }
}
