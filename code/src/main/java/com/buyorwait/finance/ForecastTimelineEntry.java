package com.buyorwait.finance;

import com.buyorwait.model.EventDirection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ForecastTimelineEntry(
        LocalDate date,
        String entryId,
        CashFlowKind kind,
        EventDirection direction,
        BigDecimal amount,
        BigDecimal balanceAfter) {
}
