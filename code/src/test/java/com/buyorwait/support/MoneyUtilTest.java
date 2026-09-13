package com.buyorwait.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyUtilTest {
    @Test
    void parsesExactDecimalWithoutFloatingPointConversion() {
        BigDecimal value = MoneyUtil.required("17229139.2", "test.csv", 2, "amount");
        assertEquals(new BigDecimal("17229139.2"), value);
    }

    @Test
    void rejectsMalformedRequiredDecimal() {
        assertThrows(IllegalArgumentException.class, () -> MoneyUtil.required("12.5x", "test.csv", 2, "amount"));
    }
}
