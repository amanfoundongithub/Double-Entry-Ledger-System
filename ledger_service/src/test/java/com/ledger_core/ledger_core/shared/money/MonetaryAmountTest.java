package com.ledger_core.ledger_core.shared.money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MonetaryAmountTest {

    @Test
    @DisplayName("Enforces 2 decimal places fixed scale")
    void enforcesFixedScaleOfTwo() {
        MonetaryAmount amount = MonetaryAmount.of("100.5");
        assertEquals(new BigDecimal("100.50"), amount.value());
        assertEquals(2, amount.value().scale());
    }

    @ParameterizedTest(name = "Banker's rounding (HALF_EVEN): {0} rounds to {1}")
    @CsvSource({
            "2.505, 2.50", // Round down to nearest even
            "2.515, 2.52", // Round up to nearest even
            "2.525, 2.52", // Round down to nearest even
            "2.535, 2.54", // Round up to nearest even
            "100.004, 100.00",
            "100.006, 100.01"
    })
    void enforcesBankersRoundingHalfEven(String input, String expected) {
        MonetaryAmount amount = MonetaryAmount.of(new BigDecimal(input));
        assertEquals(new BigDecimal(expected), amount.value());
    }

    @Test
    @DisplayName("Arithmetic operations maintain precision and scale")
    void arithmeticMaintainsScale() {
        MonetaryAmount a = MonetaryAmount.of("10.50");
        MonetaryAmount b = MonetaryAmount.of("20.25");

        MonetaryAmount sum = a.add(b);
        assertEquals(MonetaryAmount.of("30.75"), sum);

        MonetaryAmount diff = sum.subtract(a);
        assertEquals(b, diff);

        MonetaryAmount negated = a.negate();
        assertEquals(MonetaryAmount.of("-10.50"), negated);
        assertTrue(negated.isNegative());
        assertFalse(negated.isPositive());
    }

    @Test
    @DisplayName("Polarity predicates evaluate correctly")
    void polarityChecks() {
        assertTrue(MonetaryAmount.ZERO.isZero());
        assertFalse(MonetaryAmount.ZERO.isPositive());
        assertFalse(MonetaryAmount.ZERO.isNegative());

        assertTrue(MonetaryAmount.of("0.01").isPositive());
        assertFalse(MonetaryAmount.of("0.01").isNegative());

        assertTrue(MonetaryAmount.of("-0.01").isNegative());
        assertFalse(MonetaryAmount.of("-0.01").isPositive());
    }

    @Test
    @DisplayName("Rejects null value in constructor")
    void rejectsNullValue() {
        assertThrows(NullPointerException.class, () -> new MonetaryAmount(null));
    }
}