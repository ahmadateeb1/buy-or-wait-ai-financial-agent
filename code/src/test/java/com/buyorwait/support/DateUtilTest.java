package com.buyorwait.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateUtilTest {
    @Test
    void parsesIsoFinancialDatesAndPreservesBlankOptionalDates() {
        assertEquals(LocalDate.of(2026, 9, 13), DateUtil.requiredDate("2026-09-13", "test.csv", 2, "date"));
        assertTrue(DateUtil.optionalDate("", "test.csv", 2, "settlement_date").isEmpty());
    }
}
