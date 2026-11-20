package com.ledger_core.ledger_core.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record MonetaryAmount(BigDecimal value) implements Comparable<MonetaryAmount> {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;
    public static final MonetaryAmount ZERO = new MonetaryAmount(BigDecimal.ZERO);

    public MonetaryAmount {
        Objects.requireNonNull(value, "Monetary amount value must not be null");
        value = value.setScale(SCALE, ROUNDING_MODE);
    }

    public static MonetaryAmount of(BigDecimal value) {
        return new MonetaryAmount(value);
    }

    public static MonetaryAmount of(String value) {
        return new MonetaryAmount(new BigDecimal(value));
    }

    public static MonetaryAmount of(long value) {
        return new MonetaryAmount(BigDecimal.valueOf(value));
    }

    public MonetaryAmount add(MonetaryAmount other) {
        Objects.requireNonNull(other, "Operand must not be null");
        return new MonetaryAmount(this.value.add(other.value));
    }

    public MonetaryAmount subtract(MonetaryAmount other) {
        Objects.requireNonNull(other, "Operand must not be null");
        return new MonetaryAmount(this.value.subtract(other.value));
    }

    public MonetaryAmount negate() {
        return new MonetaryAmount(this.value.negate());
    }

    public MonetaryAmount abs() {
        return new MonetaryAmount(this.value.abs());
    }

    public boolean isZero() {
        return this.value.compareTo(BigDecimal.ZERO) == 0;
    }

    public boolean isPositive() {
        return this.value.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isNegative() {
        return this.value.compareTo(BigDecimal.ZERO) < 0;
    }

    @Override
    public int compareTo(MonetaryAmount other) {
        return this.value.compareTo(other.value);
    }
    
}