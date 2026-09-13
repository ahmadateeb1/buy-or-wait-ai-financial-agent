package com.buyorwait.finance;

import com.buyorwait.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class RecurrenceAnalyzer {
    private final CurrencyConverter converter;

    public RecurrenceAnalyzer(CurrencyConverter converter) {
        this.converter = converter;
    }

    public RecurrenceAnalysisResult analyze(FinancialProfile profile, LocalDate requestDate,
            List<FinancialEvent> events, List<ResolvedCashFlow> explicitFlows) {
        Map<RecurrenceKey, List<FinancialEvent>> groups = new HashMap<>();
        for (FinancialEvent event : events) {
            if (!eligible(event, requestDate)) continue;
            groups.computeIfAbsent(new RecurrenceKey(event.eventType(), event.category(), event.direction()), ignored -> new ArrayList<>())
                    .add(event);
        }
        List<ResolvedCashFlow> generated = new ArrayList<>();
        List<ForecastIssue> issues = new ArrayList<>();
        Map<String, List<BigDecimal>> historicalAmountsByOriginEventId = new HashMap<>();
        for (Map.Entry<RecurrenceKey, List<FinancialEvent>> group : groups.entrySet()) {
            List<FinancialEvent> history = group.getValue();
            history.sort(Comparator.comparing(event -> event.settlementDate().orElseThrow()));
            if (history.size() < 3) continue;
            RecurringSequence sequence = detectSequence(history);
            if (sequence == null) continue;
            Cadence cadence = sequence.cadence();
            List<BigDecimal> amounts = new ArrayList<>();
            boolean unresolved = false;
            for (FinancialEvent event : sequence.events()) {
                if (event.amount().isEmpty()) { unresolved = true; break; }
                ConversionResult result = converter.convert(event.amount().get(), event.currency(), profile.homeCurrency(),
                        event.settlementDate().orElseThrow(), event.eventId());
                result.issue().ifPresent(issues::add);
                if (result.amount().isEmpty()) { unresolved = true; break; }
                amounts.add(result.amount().get());
            }
            if (unresolved) continue;
            FinancialEvent latest = sequence.events().getLast();
            // Read-only diagnostic evidence: chronological, selected-series amounts in home currency.
            historicalAmountsByOriginEventId.put(latest.eventId(), List.copyOf(amounts));
            BigDecimal amount = recurringAmount(amounts, group.getKey().direction());
            LocalDate occurrence = cadence.nextAfter(latest.settlementDate().orElseThrow());
            LocalDate end = requestDate.plusDays(89);
            while (!occurrence.isAfter(end)) {
                if (!occurrence.isBefore(requestDate) && !hasExplicitFlow(explicitFlows, occurrence, group.getKey())) {
                    generated.add(new ResolvedCashFlow("recurrence:%s:%s".formatted(latest.eventId(), occurrence),
                            Optional.of(latest.eventId()), occurrence, group.getKey().direction(), amount,
                            CashFlowKind.RECURRENCE, true, latest.flexibility(), latest.category()));
                }
                occurrence = cadence.nextAfter(occurrence);
            }
        }
        return new RecurrenceAnalysisResult(generated, issues, historicalAmountsByOriginEventId);
    }

    private static boolean eligible(FinancialEvent event, LocalDate requestDate) {
        if (event.status() != EventStatus.SETTLED || event.direction() == EventDirection.NON_CASH || event.amount().isEmpty()
                || event.settlementDate().isEmpty() || !event.settlementDate().get().isBefore(requestDate)) return false;
        return event.eventType() != EventType.REFUND && event.eventType() != EventType.INVESTMENT_PURCHASE
                && event.eventType() != EventType.INVESTMENT_SALE && event.eventType() != EventType.INVESTMENT_VALUATION;
    }

    private static RecurringSequence detectSequence(List<FinancialEvent> history) {
        NavigableMap<LocalDate, List<FinancialEvent>> byDate = new TreeMap<>();
        for (FinancialEvent event : history) {
            byDate.computeIfAbsent(event.settlementDate().orElseThrow(), ignored -> new ArrayList<>()).add(event);
        }
        // Multiple records on one date do not provide multiple occurrences of a cadence.
        byDate.values().forEach(events -> events.sort(Comparator.comparing(FinancialEvent::eventId)));
        List<LocalDate> dates = new ArrayList<>(byDate.keySet());
        RecurringSequence strongest = null;
        for (int i = 0; i < dates.size(); i++) {
            LocalDate first = dates.get(i);
            strongest = stronger(strongest, sequenceFrom(first, Cadence.MONTHLY, byDate));
            for (int j = i + 1; j < dates.size(); j++) {
                long gap = ChronoUnit.DAYS.between(first, dates.get(j));
                strongest = stronger(strongest, sequenceFrom(first, new FixedDayCadence(gap), byDate));
            }
        }
        return strongest;
    }

    private static RecurringSequence sequenceFrom(LocalDate first, Cadence cadence,
            Map<LocalDate, List<FinancialEvent>> byDate) {
        List<FinancialEvent> events = new ArrayList<>();
        int occurrences = 0;
        for (LocalDate date = first; byDate.containsKey(date); date = cadence.nextAfter(date)) {
            events.addAll(byDate.get(date));
            occurrences++;
        }
        return occurrences >= 3 ? new RecurringSequence(events, cadence, occurrences) : null;
    }

    private static RecurringSequence stronger(RecurringSequence current, RecurringSequence candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        // Prefer more supported dates; ties retain the existing monthly preference,
        // then the more recent sequence. Sorted dates make any remaining tie deterministic.
        if (candidate.occurrences() != current.occurrences()) {
            return candidate.occurrences() > current.occurrences() ? candidate : current;
        }
        if ((candidate.cadence() == Cadence.MONTHLY) != (current.cadence() == Cadence.MONTHLY)) {
            return candidate.cadence() == Cadence.MONTHLY ? candidate : current;
        }
        LocalDate candidateLatest = candidate.events().getLast().settlementDate().orElseThrow();
        LocalDate currentLatest = current.events().getLast().settlementDate().orElseThrow();
        return candidateLatest.isAfter(currentLatest) ? candidate : current;
    }

    private record RecurringSequence(List<FinancialEvent> events, Cadence cadence, int occurrences) { }

    private static BigDecimal recurringAmount(List<BigDecimal> amounts, EventDirection direction) {
        boolean constant = amounts.stream().allMatch(amount -> amount.compareTo(amounts.getFirst()) == 0);
        if (constant) return amounts.getFirst();
        return direction == EventDirection.DEBIT
                ? amounts.stream().max(BigDecimal::compareTo).orElseThrow()
                : amounts.stream().min(BigDecimal::compareTo).orElseThrow();
    }

    private static boolean hasExplicitFlow(List<ResolvedCashFlow> flows, LocalDate date, RecurrenceKey key) {
        return flows.stream().anyMatch(flow -> flow.date().equals(date) && flow.direction() == key.direction()
                && flow.category().equals(key.category()));
    }

    private record RecurrenceKey(EventType type, String category, EventDirection direction) {
    }

    private sealed interface Cadence permits FixedDayCadence, MonthlyCadence {
        Cadence MONTHLY = new MonthlyCadence();
        LocalDate nextAfter(LocalDate date);
    }

    private record FixedDayCadence(long days) implements Cadence {
        public LocalDate nextAfter(LocalDate date) { return date.plusDays(days); }
    }

    private static final class MonthlyCadence implements Cadence {
        public LocalDate nextAfter(LocalDate date) { return date.plusMonths(1); }
    }
}
