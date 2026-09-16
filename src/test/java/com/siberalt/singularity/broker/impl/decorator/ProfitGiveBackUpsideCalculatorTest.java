package com.siberalt.singularity.broker.impl.decorator;

import com.siberalt.singularity.broker.contract.value.money.Money;
import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.entity.operation.Operation;
import com.siberalt.singularity.entity.operation.OperationState;
import com.siberalt.singularity.entity.operation.OperationType;
import com.siberalt.singularity.entity.operation.ReadOperationRepository;
import com.siberalt.singularity.shared.TimeRange;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfitGiveBackUpsideCalculatorTest {
    private static final String ACCOUNT = "account";
    private static final String INSTRUMENT = "TEST";
    private static final String ANOTHER = "OTHER";
    private static final Instant START = Instant.parse("2024-01-01T00:00:00Z");
    private static final Money ALLOCATED = Money.of("RUB", 1000.0);
    private static final Duration WAIT = Duration.ofDays(5);

    private final List<Operation> operations = new ArrayList<>();
    private final ReadOperationRepository repository = mock(ReadOperationRepository.class);
    private final UpsideCalculator signal = candles -> new Upside(1, 1);

    /**
     * The guard asks for one instrument's operations within a window and folds each one once, so the
     * double has to honour both - a repository that answered everything would hide the bookkeeping.
     */
    ProfitGiveBackUpsideCalculatorTest() {
        when(repository.getByAccountIdAndInstrumentUid(anyString(), anyString(), any()))
            .thenAnswer(invocation -> {
                String instrument = invocation.getArgument(1);
                TimeRange range = invocation.getArgument(2);

                return operations.stream()
                    .filter(operation -> operation.instrumentUid().equals(instrument))
                    .filter(operation -> !operation.executedDate().isBefore(range.from()))
                    .filter(operation -> !operation.executedDate().isAfter(range.to()))
                    .toList();
            });
    }

    @Test
    void letsTheSignalThroughWhileNothingHasBeenGivenBack() {
        assertEquals(new Upside(1, 1), guard().calculate(bars(0, 100)));
    }

    /**
     * The point of a give-back rather than a drawdown: at a peak of a thousand made, three hundred is
     * as much as may go back, so it stops while it is still seven hundred ahead.
     */
    @Test
    void stopsOnceTooMuchOfTheProfitHasGoneBack() {
        bought(10, 100, INSTRUMENT);
        ProfitGiveBackUpsideCalculator guard = guard();

        assertEquals(new Upside(1, 1), guard.calculate(bars(0, 200)));
        assertEquals(new Upside(1, 1), guard.calculate(bars(1, 171)));

        assertEquals(new Upside(-1, 1), guard.calculate(bars(2, 165)));
        assertEquals(1, guard.getTrips());
    }

    /** Before there is a profit to hand back, the loss budget is what it is measured against. */
    @Test
    void spendsOnlyItsLossBudgetBeforeItEverProfits() {
        bought(10, 100, INSTRUMENT);
        ProfitGiveBackUpsideCalculator guard = guard();

        assertEquals(new Upside(1, 1), guard.calculate(bars(0, 100)));
        assertEquals(new Upside(1, 1), guard.calculate(bars(1, 91)));

        assertEquals(new Upside(-1, 1), guard.calculate(bars(2, 88)));
    }

    /**
     * The reason this is not a guard on the account. Another instrument losing far more than this one
     * has is none of its business - selling what is doing well to answer for what is not.
     */
    @Test
    void ignoresWhatHappensInOtherInstruments() {
        bought(10, 100, INSTRUMENT);
        bought(100, 1000, ANOTHER);
        ProfitGiveBackUpsideCalculator guard = guard();

        assertEquals(new Upside(1, 1), guard.calculate(bars(0, 100)));
        assertEquals(0, guard.getTrips());
    }

    /**
     * The book is kept, not rebuilt: asked on every bar of a long run, it must fold each operation
     * exactly once. Counting the same purchase twice would show a loss that was never taken and stop
     * a strategy that is level.
     */
    @Test
    void foldsEachOperationOnceHoweverOftenItIsAsked() {
        bought(10, 100, INSTRUMENT);
        ProfitGiveBackUpsideCalculator guard = guard();

        for (int hour = 0; hour < 50; hour++) {
            assertEquals(new Upside(1, 1), guard.calculate(bars(hour, 100)));
        }

        assertEquals(0, guard.getTrips());
    }

    @Test
    void staysOutWhileItWaits() {
        bought(10, 100, INSTRUMENT);
        ProfitGiveBackUpsideCalculator guard = guard();

        guard.calculate(bars(0, 100));
        guard.calculate(bars(1, 88));
        sold(10, 88, INSTRUMENT);

        assertEquals(Upside.NEUTRAL, guard.calculate(bars(2, 88)));
        assertEquals(Upside.NEUTRAL, guard.calculate(bars(24 * 4, 88)));
    }

    /**
     * Out of the market the curve is flat, so a give-back never shrinks by itself. The wait is what
     * lifts the guard, and the high water mark moves down to meet the curve - otherwise the same loss
     * would stop it again on the first bar back.
     */
    @Test
    void comesBackWhenTheWaitIsOutAndDoesNotTripOnTheSameLoss() {
        bought(10, 100, INSTRUMENT);
        ProfitGiveBackUpsideCalculator guard = guard();

        guard.calculate(bars(0, 100));
        guard.calculate(bars(1, 88));
        sold(10, 88, INSTRUMENT);

        assertEquals(new Upside(1, 1), guard.calculate(bars(24 * 6, 88)));
        assertEquals(new Upside(1, 1), guard.calculate(bars(24 * 7, 88)));
        assertEquals(1, guard.getTrips());
    }

    @Test
    void refusesToBeBuiltWithNothingToGuard() {
        assertThrows(IllegalArgumentException.class,
            () -> new ProfitGiveBackUpsideCalculator(null, ACCOUNT, INSTRUMENT, repository, ALLOCATED));
        assertThrows(IllegalArgumentException.class,
            () -> new ProfitGiveBackUpsideCalculator(signal, ACCOUNT, INSTRUMENT, repository, ALLOCATED, 0, 0.1, WAIT));
        assertThrows(IllegalArgumentException.class,
            () -> new ProfitGiveBackUpsideCalculator(signal, ACCOUNT, INSTRUMENT, repository, ALLOCATED, 1.5, 0.1, WAIT));
        assertThrows(IllegalArgumentException.class,
            () -> new ProfitGiveBackUpsideCalculator(signal, ACCOUNT, INSTRUMENT, repository, ALLOCATED, 0.3, 0, WAIT));
        assertThrows(IllegalArgumentException.class,
            () -> new ProfitGiveBackUpsideCalculator(signal, ACCOUNT, INSTRUMENT, repository, ALLOCATED, 0.3, 0.1, Duration.ofDays(-1)));
    }

    private ProfitGiveBackUpsideCalculator guard() {
        return new ProfitGiveBackUpsideCalculator(signal, ACCOUNT, INSTRUMENT, repository, ALLOCATED, 0.3, 0.1, WAIT);
    }

    private void bought(long quantity, double price, String instrument) {
        operations.add(operation(OperationType.BUY, quantity, price, instrument));
    }

    private void sold(long quantity, double price, String instrument) {
        operations.add(operation(OperationType.SELL, quantity, price, instrument));
    }

    private Operation operation(OperationType direction, long quantity, double price, String instrument) {
        return Operation.builder()
            .id(String.valueOf(operations.size()))
            .accountId(ACCOUNT)
            .instrumentUid(instrument)
            .direction(direction)
            .quantity(quantity)
            .quantityDone(quantity)
            .price(Quotation.of(price))
            .payment(Quotation.of(quantity * price))
            .state(OperationState.EXECUTED)
            .date(START)
            .executedDate(START)
            .build();
    }

    private List<Candle> bars(int hour, double close) {
        Quotation price = Quotation.of(close);

        return List.of(new Candle(
            1L,
            new TimePoint(START.plus(Duration.ofHours(hour))),
            price, price, price, price, 0
        ));
    }
}
