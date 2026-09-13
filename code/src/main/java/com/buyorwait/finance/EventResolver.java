package com.buyorwait.finance;

import com.buyorwait.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class EventResolver {
    private final CurrencyConverter converter;

    public EventResolver(CurrencyConverter converter) {
        this.converter = converter;
    }

    public ResolutionResult resolve(FinancialProfile profile, LocalDate requestDate, List<FinancialEvent> events) {
        return resolve(profile, requestDate, events, List.of());
    }

    public ResolutionResult resolve(FinancialProfile profile, LocalDate requestDate, List<FinancialEvent> events,
            List<EventResolutionOverride> overrides) {
        List<ResolvedCashFlow> flows = new ArrayList<>();
        List<ForecastIssue> issues = new ArrayList<>();
        LocalDate horizonEnd = requestDate.plusDays(89);
        for (FinancialEvent event : applyOverrides(events, overrides)) {
            ResolvedEvent resolved = applyOverride(event, null);
            if (resolved.status() == EventStatus.CANCELLED || resolved.status() == EventStatus.FAILED
                    || resolved.direction() == EventDirection.NON_CASH || resolved.status() == EventStatus.UNREALIZED) {
                continue;
            }
            if (resolved.status() == EventStatus.PENDING && resolved.direction() == EventDirection.CREDIT) continue;
            if (resolved.status() == EventStatus.SCHEDULED && resolved.direction() == EventDirection.CREDIT
                    && event.category().equals("salary") == false) continue;
            Optional<LocalDate> settlement = resolved.settlementDate();
            if (settlement.isEmpty()) {
                if (requiresForecast(resolved, requestDate, horizonEnd)) {
                    issues.add(ForecastIssue.forEvent("missing_settlement_date", "Cash event requires a settlement date", event.eventId()));
                }
                continue;
            }
            LocalDate effectiveDate = resolved.status() == EventStatus.PENDING && resolved.direction() == EventDirection.DEBIT
                    ? requestDate : settlement.get();
            if (effectiveDate.isBefore(requestDate) || effectiveDate.isAfter(horizonEnd)) continue;
            if (resolved.amount().isEmpty()) {
                issues.add(ForecastIssue.forEvent("unresolved_amount", "Cash event has no resolved amount", event.eventId()));
                continue;
            }
            ConversionResult converted = converter.convert(resolved.amount().get(), event.currency(), profile.homeCurrency(),
                    settlement.get(), event.eventId());
            converted.issue().ifPresent(issues::add);
            converted.amount().ifPresent(amount -> flows.add(new ResolvedCashFlow(
                    event.eventId(), Optional.of(event.eventId()), effectiveDate, resolved.direction(), amount,
                    resolved.status() == EventStatus.PENDING ? CashFlowKind.PENDING_DEBIT_RESERVATION : CashFlowKind.RESOLVED_EVENT,
                    false, event.flexibility(), event.category())));
        }
        return new ResolutionResult(flows, issues);
    }

    private static boolean requiresForecast(ResolvedEvent event, LocalDate start, LocalDate end) {
        return event.status() == EventStatus.PENDING || event.status() == EventStatus.SCHEDULED || event.status() == EventStatus.SETTLED;
    }

    public List<FinancialEvent> applyOverrides(List<FinancialEvent> events, List<EventResolutionOverride> overrides) {
        return events.stream().map(event -> {
            EventResolutionOverride override = selectOverride(event, overrides);
            if (override == null) return event;
            return new FinancialEvent(event.eventId(), event.userId(), event.eventType(), event.description(), event.category(),
                    event.direction(), override.amount().isPresent() ? override.amount() : event.amount(),
                    override.currency().orElse(event.currency()), event.eventDate(),
                    override.settlementDate().isPresent() ? override.settlementDate() : event.settlementDate(),
                    override.status().orElse(event.status()), event.linkedEventId(), event.flexibility(), event.minimumAllowedAmount());
        }).toList();
    }

    public static EventResolutionOverride selectOverride(FinancialEvent event, List<EventResolutionOverride> overrides) {
        List<EventResolutionOverride> matching = overrides.stream().filter(override -> override.eventId().equals(event.eventId())).toList();
        if (matching.stream().anyMatch(EventResolutionOverride::explicit)) {
            matching = matching.stream().filter(EventResolutionOverride::explicit).toList();
        }
        Map<String, EventResolutionOverride> newestPerSource = new HashMap<>();
        matching.forEach(candidate ->
                newestPerSource.merge(candidate.source(), candidate,
                        (left, right) -> left.observedAt().equals(right.observedAt())
                                ? financiallySafer(event, List.of(left, right))
                                : left.observedAt().isAfter(right.observedAt()) ? mergeAmendments(right, left) : mergeAmendments(left, right)));
        List<EventResolutionOverride> candidates = new ArrayList<>(newestPerSource.values());
        if (candidates.isEmpty()) return null;
        List<EventResolutionOverride> explicit = candidates.stream().filter(EventResolutionOverride::explicit).toList();
        if (!explicit.isEmpty()) candidates = explicit;
        List<EventResolutionOverride> settled = candidates.stream()
                .filter(override -> override.status().orElse(event.status()) == EventStatus.SETTLED).toList();
        if (!settled.isEmpty()) candidates = settled;
        return financiallySafer(event, candidates);
    }

    private static EventResolutionOverride mergeAmendments(EventResolutionOverride older, EventResolutionOverride newer) {
        // A revised date does not retract an earlier amount confirmation from the same source.
        return new EventResolutionOverride(newer.eventId(), newer.source(), newer.observedAt(), newer.explicit(),
                newer.status().isPresent() ? newer.status() : older.status(),
                newer.amount().isPresent() ? newer.amount() : older.amount(),
                newer.settlementDate().isPresent() ? newer.settlementDate() : older.settlementDate(),
                newer.currency().isPresent() ? newer.currency() : older.currency());
    }

    private static EventResolutionOverride financiallySafer(FinancialEvent event, List<EventResolutionOverride> candidates) {
        Comparator<EventResolutionOverride> amountComparator = Comparator.comparing(override -> override.amount().orElseGet(
                () -> event.amount().orElse(BigDecimal.ZERO)), BigDecimal::compareTo);
        Comparator<EventResolutionOverride> safer = event.direction() == EventDirection.DEBIT ? amountComparator : amountComparator.reversed();
        return candidates.stream().max(safer.thenComparing(EventResolutionOverride::observedAt)).orElseThrow();
    }

    private static ResolvedEvent applyOverride(FinancialEvent event, EventResolutionOverride override) {
        if (override == null) return new ResolvedEvent(event.status(), event.direction(), event.amount(), event.settlementDate());
        return new ResolvedEvent(override.status().orElse(event.status()), event.direction(),
                override.amount().isPresent() ? override.amount() : event.amount(),
                override.settlementDate().isPresent() ? override.settlementDate() : event.settlementDate());
    }

    private record ResolvedEvent(EventStatus status, EventDirection direction, Optional<BigDecimal> amount,
                                 Optional<LocalDate> settlementDate) {
    }
}
