package com.siberalt.singularity.broker.contract.value.quotation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class QuotationTest {

    @Test
    void quotationsOfTheSameValueBuiltDifferentlyAreEqualAndHashEqually() {
        assertAllEqualWithEqualHashes(List.of(
            Quotation.of(1, 500_000_000),
            Quotation.of(0, 1_500_000_000),
            Quotation.of(2, -500_000_000),
            Quotation.of(new BigDecimal("1.5")),
            Quotation.of(new BigDecimal("1.50")),
            Quotation.of(new BigDecimal("1.500000000000")),
            Quotation.of("1.5"),
            Quotation.of(1.5),
            Quotation.fromLong(1_500_000_000L),
            Quotation.of(1).add(Quotation.of(0, 500_000_000))
        ));
    }

    @Test
    void zeroBuiltDifferentlyIsEqualAndHashesEqually() {
        // (1, -10^9) prints as "0E-9" and Quotation.ZERO as "0", so hashing toString() would split them
        assertNotEquals(Quotation.ZERO.toString(), Quotation.of(1, -1_000_000_000).toString());

        assertAllEqualWithEqualHashes(List.of(
            Quotation.ZERO,
            Quotation.of(0, 0),
            Quotation.of(1, -1_000_000_000),
            Quotation.of(0L),
            Quotation.of(0.0),
            Quotation.of(BigDecimal.ZERO),
            Quotation.fromLong(0),
            Quotation.of(5).subtract(Quotation.of(5))
        ));
    }

    @Test
    void negativeValuesBuiltDifferentlyAreEqualAndHashEqually() {
        assertAllEqualWithEqualHashes(List.of(
            Quotation.of(-1, -500_000_000),
            Quotation.of(-2, 500_000_000),
            Quotation.of(new BigDecimal("-1.5")),
            Quotation.of(-1.5),
            Quotation.fromLong(-1_500_000_000L)
        ));
    }

    @Test
    void unitsLargeEnoughToOverflowNanosStillHashEqually() {
        long units = Long.MAX_VALUE / 100;

        assertAllEqualWithEqualHashes(List.of(
            Quotation.of(units, 0),
            Quotation.of(units - 1, 1_000_000_000),
            Quotation.of(units + 1, -1_000_000_000)
        ));
    }

    @Test
    void differentValuesAreNotEqual() {
        assertNotEquals(Quotation.of(1, 500_000_000), Quotation.of(1, 500_000_001));
        assertNotEquals(Quotation.of(1, 500_000_000).hashCode(), Quotation.of(1, 500_000_001).hashCode());
        assertNotEquals(Quotation.of(1, 500_000_000), Quotation.of(-1, -500_000_000));
    }

    @Test
    void settingFieldsAfterTheValueWasReadIsSeenByEqualsAndHashCode() {
        Quotation quotation = Quotation.of(1, 0);
        quotation.toBigDecimal();

        quotation.setUnits(2).setNano(250_000_000);

        assertEquals(Quotation.of(new BigDecimal("2.25")), quotation);
        assertEquals(Quotation.of(new BigDecimal("2.25")).hashCode(), quotation.hashCode());
        assertEquals(new BigDecimal("2.25"), quotation.toBigDecimal().stripTrailingZeros());
    }

    @Test
    void equalQuotationsCollapseInAHashSet() {
        Set<Quotation> set = new HashSet<>(List.of(
            Quotation.of(1, 500_000_000),
            Quotation.of(0, 1_500_000_000),
            Quotation.of(1.5),
            Quotation.of(2.5)
        ));

        assertEquals(2, set.size());
        assertTrue(set.contains(Quotation.of(2, -500_000_000)));
    }

    private static void assertAllEqualWithEqualHashes(List<Quotation> quotations) {
        Quotation first = quotations.getFirst();
        for (Quotation quotation : quotations) {
            assertEquals(first, quotation, () -> quotation + " should equal " + first);
            assertEquals(quotation, first, () -> first + " should equal " + quotation);
            assertEquals(first.hashCode(), quotation.hashCode(),
                () -> "hash of " + quotation + " (" + quotation.getUnits() + ", " + quotation.getNano()
                    + ") should equal hash of " + first);
        }
    }
}
