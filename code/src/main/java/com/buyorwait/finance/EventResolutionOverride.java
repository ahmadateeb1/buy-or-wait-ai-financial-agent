package com.buyorwait.finance;

import com.buyorwait.model.EventStatus;
import com.buyorwait.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** A typed amendment supplied by a later evidence-extraction phase. */
public record EventResolutionOverride(
        String eventId,
        String source,
        Instant observedAt,
        boolean explicit,
        Optional<EventStatus> status,
        Optional<BigDecimal> amount,
        Optional<LocalDate> settlementDate,
        Optional<CurrencyCode> currency) {
    public EventResolutionOverride(String eventId, String source, Instant observedAt, boolean explicit,
            Optional<EventStatus> status, Optional<BigDecimal> amount, Optional<LocalDate> settlementDate) {
        this(eventId, source, observedAt, explicit, status, amount, settlementDate, Optional.empty());
    }
    public EventResolutionOverride {
        status = status == null ? Optional.empty() : status;
        amount = amount == null ? Optional.empty() : amount;
        settlementDate = settlementDate == null ? Optional.empty() : settlementDate;
        currency = currency == null ? Optional.empty() : currency;
    }
}
