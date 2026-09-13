package com.buyorwait.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public record FinancialEvent(
        String eventId,
        String userId,
        EventType eventType,
        String description,
        String category,
        EventDirection direction,
        Optional<BigDecimal> amount,
        CurrencyCode currency,
        LocalDate eventDate,
        Optional<LocalDate> settlementDate,
        EventStatus status,
        Optional<String> linkedEventId,
        Flexibility flexibility,
        Optional<BigDecimal> minimumAllowedAmount) {
}
