package com.java.wallet.utils.helpers;

import java.math.BigDecimal;
import java.math.RoundingMode;

// * All monetary amounts are fixed-precision decimals with exactly two fraction digits
public final class Money {

    public static final int SCALE = 2;

    private Money() {
    }

    public static BigDecimal of(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
