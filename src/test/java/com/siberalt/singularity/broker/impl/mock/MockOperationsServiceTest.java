package com.siberalt.singularity.broker.impl.mock;

import com.siberalt.singularity.broker.contract.service.operation.request.GetPositionsRequest;
import com.siberalt.singularity.broker.contract.service.user.AccessLevel;
import com.siberalt.singularity.broker.contract.service.user.Account;
import com.siberalt.singularity.broker.contract.service.user.AccountType;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.broker.impl.mock.shared.operation.OpenPosition;
import com.siberalt.singularity.strategy.context.simulation.time.ClockStub;
import com.siberalt.singularity.strategy.simulation.SimulationContext;
import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.exception.InvalidRequestException;
import com.siberalt.singularity.broker.contract.service.exception.NotFoundException;
import com.siberalt.singularity.broker.contract.value.money.Money;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public class MockOperationsServiceTest {
    @Test
    void testBasic() throws AbstractException {
        MockBroker mockBroker = new MockBroker(null, null, null);
        ClockStub clock = new ClockStub();
        clock.syncCurrentTime(Instant.parse("2020-12-30T07:00:00Z"));
        mockBroker.applyContext(new SimulationContext(null, null, clock));
        MockOperationsService operationsService = mockBroker.getOperationsService();

        Account account = mockBroker
            .getUserService()
            .openAccount("testAccount", AccountType.ORDINARY, AccessLevel.FULL_ACCESS);

        // Test addMoney
        operationsService.addMoney(account.getId(), Money.of("RUB", 12000D));
        operationsService.addMoney(account.getId(), Money.of("USD", 720000D));
        operationsService.addMoney(account.getId(), Money.of("BTC", 1D));

        var response = operationsService.getPositions(GetPositionsRequest.of(account.getId()));
        Assertions.assertEquals(3, response.getMoney().size());

        Function<String, List<Money>> currencyBalanceGetter = currency ->
            response.getMoney()
                .stream()
                .filter(x -> x.getCurrencyIso().equals(currency))
                .toList();

        List<Money> moneyBalance = currencyBalanceGetter.apply("RUB");
        Assertions.assertEquals(1, moneyBalance.size());
        Money money = moneyBalance.stream().findFirst().orElseThrow();
        Assertions.assertEquals(Quotation.of(12000D), money.getQuotation());

        moneyBalance = currencyBalanceGetter.apply("USD");
        Assertions.assertEquals(1, moneyBalance.size());
        money = moneyBalance.stream().findFirst().orElseThrow();
        Assertions.assertEquals(Quotation.of(720000D), money.getQuotation());

        moneyBalance = currencyBalanceGetter.apply("BTC");
        Assertions.assertEquals(1, moneyBalance.size());
        money = moneyBalance.stream().findFirst().orElseThrow();
        Assertions.assertEquals(Quotation.of(1D), money.getQuotation());

        operationsService.addMoney(account.getId(), Money.of("RUB", 1000D));
        money = operationsService.getAvailableMoney(account.getId(), "RUB");
        Assertions.assertNotNull(money);
        Assertions.assertEquals("RUB", money.getCurrencyIso());
        Assertions.assertEquals(Quotation.of(13000D), money.getQuotation());

        // Test subtractMoney
        operationsService.subtractMoney(account.getId(), Money.of("USD", 5000D));
        money = operationsService.getAvailableMoney(account.getId(), "USD");
        Assertions.assertNotNull(money);
        Assertions.assertEquals("USD", money.getCurrencyIso());
        Assertions.assertEquals(Quotation.of(715000D), money.getQuotation());

        // Test openPosition
        operationsService.openPosition(
            account.getId(),
            new OpenPosition()
                .setInitialBalance(12)
                .setInstrumentId("SBER")
                .setInstrumentType("SHARE")
        );
        var position = operationsService.getPositionByInstrumentId(account.getId(), "SBER");
        Assertions.assertNotNull(position);
        Assertions.assertEquals(12, position.getBalance());
        Assertions.assertEquals("SBER", position.getInstrumentUid());
        Assertions.assertEquals("SHARE", position.getInstrumentType());
        Assertions.assertEquals(0, position.getBlocked());

        // Test addToPosition
        operationsService.addToPosition(account.getId(), "SBER", 50);
        position = operationsService.getPositionByInstrumentId(account.getId(), "SBER");
        Assertions.assertEquals(62, position.getBalance());

        // Test subtractFromPosition
        operationsService.subtractFromPosition(account.getId(), "SBER", 10);
        position = operationsService.getPositionByInstrumentId(account.getId(), "SBER");
        Assertions.assertEquals(52, position.getBalance());

        // Test isEnoughOfMoney
        Assertions.assertFalse(
            operationsService.isEnoughOfMoney(
                account.getId(),
                Money.of("BTC", 100D)
            )
        );
        Assertions.assertTrue(
            operationsService.isEnoughOfMoney(
                account.getId(),
                Money.of("BTC", 0.5)
            )
        );

        // Test closePosition
        operationsService.closePosition(account.getId(), "SBER");
        Assertions.assertNull(operationsService.getPositionByInstrumentId(account.getId(), "SBER"));

        // Test blockMoney
        operationsService.blockMoney(account.getId(), Money.of("RUB", 12000D));
        Assertions.assertFalse(
            operationsService.isEnoughOfMoney(account.getId(), Money.of("RUB", 10000D))
        );
        Assertions.assertTrue(
            operationsService.isEnoughOfMoney(account.getId(), Money.of("RUB", 1000D))
        );
        Assertions.assertEquals(
            Money.of("RUB", 1000D),
            operationsService.getAvailableMoney(account.getId(), "RUB")
        );
        Assertions.assertThrowsExactly(
            InvalidRequestException.class,
            () -> operationsService.blockMoney(account.getId(), Money.of("RUB", 1000000D))
        );

        // Test unblockMoney
        operationsService.unblockMoney(account.getId(), Money.of("RUB", 10000D));
        Assertions.assertTrue(
            operationsService.isEnoughOfMoney(account.getId(), Money.of("RUB", 10000D))
        );
        Assertions.assertEquals(
            Money.of("RUB", 11000D),
            operationsService.getAvailableMoney(account.getId(), "RUB")
        );

        // Test account not found
        Assertions.assertThrows(
            NotFoundException.class,
            () -> operationsService.addMoney(UUID.randomUUID().toString(), Money.of("RUB", 12D))
        );
    }
}

