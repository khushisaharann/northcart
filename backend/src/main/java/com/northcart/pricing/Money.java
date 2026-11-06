package com.northcart.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Small helpers so every amount in an API response has exactly 2 decimals
 * (49.00, not 49 or 49.0), matching NUMERIC(12,2) in the database.
 */
public final class Money {

    private Money() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal times(BigDecimal unitPrice, int quantity) {
        return of(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }
}
