package com.buyorwait.finance;

import com.buyorwait.input.DatasetIndex;
import com.buyorwait.model.*;
import com.buyorwait.evidence.*;

import java.time.LocalDate;
import java.util.*;

/** Composes event resolution, recurrence projection, and the pure forecast simulation. */
public final class CashFlowForecastService {
    private final DatasetIndex index;
    private final EventResolver eventResolver;
    private final RecurrenceAnalyzer recurrenceAnalyzer;
    private final ForecastEngine forecastEngine;
    private final MessageEvidenceLayer messageEvidence;

    public CashFlowForecastService(DatasetIndex index) {
        this(index, new DeterministicMessageEvidenceParser());
    }

    public CashFlowForecastService(DatasetIndex index, MessageEvidenceParser parser) {
        this.index = index;
        CurrencyConverter converter = new CurrencyConverter(index);
        this.eventResolver = new EventResolver(converter);
        this.recurrenceAnalyzer = new RecurrenceAnalyzer(converter);
        this.forecastEngine = new ForecastEngine();
        this.messageEvidence = new MessageEvidenceLayer(parser);
    }

    public ForecastDiagnostic diagnose(FinanceRequest request, List<ProposedPayment> payments, List<SpendingAdjustment> adjustments) {
        FinancialProfile profile = index.profileByUserId(request.userId()).orElseThrow();
        return diagnose(profile, request.requestDate(), index.eventsByUserId(request.userId()), payments, adjustments,
                Optional.of(request.requestId()));
    }

    public ForecastResult simulate(FinanceRequest request, List<ProposedPayment> payments, List<SpendingAdjustment> adjustments) {
        return diagnose(request, payments, adjustments).result();
    }

    public ForecastResult simulate(FinancialProfile profile, LocalDate requestDate, List<ProposedPayment> payments,
            List<SpendingAdjustment> adjustments) {
        return simulate(profile, requestDate, index.eventsByUserId(profile.userId()), payments, adjustments);
    }

    public ForecastResult simulate(FinancialProfile profile, LocalDate requestDate,
            List<com.buyorwait.model.FinancialEvent> events, List<ProposedPayment> payments,
            List<SpendingAdjustment> adjustments) {
        return diagnose(profile, requestDate, events, payments, adjustments).result();
    }

    public ForecastDiagnostic diagnose(FinancialProfile profile, LocalDate requestDate, List<ProposedPayment> payments,
            List<SpendingAdjustment> adjustments) {
        return diagnose(profile, requestDate, index.eventsByUserId(profile.userId()), payments, adjustments);
    }

    public ForecastDiagnostic diagnose(FinancialProfile profile, LocalDate requestDate,
            List<com.buyorwait.model.FinancialEvent> events, List<ProposedPayment> payments,
            List<SpendingAdjustment> adjustments) {
        return diagnose(profile, requestDate, events, payments, adjustments, Optional.empty());
    }

    private ForecastDiagnostic diagnose(FinancialProfile profile, LocalDate requestDate, List<FinancialEvent> events,
            List<ProposedPayment> payments, List<SpendingAdjustment> adjustments, Optional<String> requestId) {
        var extraction = messageEvidence.extract(profile.userId(), requestDate, requestId, index.messagesByUserId(profile.userId()));
        var linked = messageEvidence.bindLinked(events, extraction);
        List<FinancialEvent> amendedEvents = eventResolver.applyOverrides(events, linked.values());
        ResolutionResult initial = eventResolver.resolve(profile, requestDate, amendedEvents);
        Set<String> excluded = messageEvidence.nonRecurringEventIds(extraction);
        RecurrenceAnalysisResult recurrence = recurrenceAnalyzer.analyze(profile, requestDate,
                amendedEvents.stream().filter(event -> !excluded.contains(event.eventId())).toList(), initial.cashFlows());
        Map<String, ResolvedCashFlow> projected = new HashMap<>();
        List<FinancialEvent> combined = new ArrayList<>(amendedEvents);
        for (ResolvedCashFlow flow : recurrence.cashFlows()) {
            projected.put(flow.flowId(), flow);
            EventType type = events.stream().filter(event -> flow.originEventId().filter(event.eventId()::equals).isPresent())
                    .map(FinancialEvent::eventType).findFirst().orElseThrow();
            combined.add(new FinancialEvent(flow.flowId(), profile.userId(), type, "Projected occurrence", flow.category(),
                    flow.direction(), Optional.of(flow.amount()), profile.homeCurrency(), flow.date(), Optional.of(flow.date()),
                    EventStatus.SCHEDULED, Optional.empty(), flow.flexibility(), Optional.empty()));
        }
        var occurrences = messageEvidence.bindOccurrences(profile, requestDate, combined, projected.keySet(), extraction);
        List<EventResolutionOverride> overrides = occurrences.values();
        // Inferred non-salary credits retain their existing cash treatment: only re-resolve amended projections.
        Set<String> overriddenIds = new HashSet<>();
        overrides.forEach(override -> overriddenIds.add(override.eventId()));
        List<FinancialEvent> toResolve = occurrences.events().stream()
                .filter(event -> !projected.containsKey(event.eventId()) || overriddenIds.contains(event.eventId())).toList();
        ResolutionResult resolution = eventResolver.resolve(profile, requestDate, toResolve, overrides);
        List<ResolvedCashFlow> cashFlows = new ArrayList<>();
        projected.values().stream().filter(flow -> !overriddenIds.contains(flow.flowId())).forEach(cashFlows::add);
        Set<String> linkedIds = new HashSet<>();
        linked.values().forEach(override -> linkedIds.add(override.eventId()));
        Set<String> originalIds = new HashSet<>();
        events.forEach(event -> originalIds.add(event.eventId()));
        for (ResolvedCashFlow flow : resolution.cashFlows()) {
            ResolvedCashFlow original = projected.get(flow.flowId());
            boolean messageDerived = original != null || overriddenIds.contains(flow.flowId()) || linkedIds.contains(flow.flowId());
            if (!messageDerived) { cashFlows.add(flow); continue; }
            cashFlows.add(new ResolvedCashFlow(flow.flowId(), original != null ? original.originEventId()
                    : originalIds.contains(flow.flowId()) ? flow.originEventId() : Optional.empty(), flow.date(), flow.direction(),
                    flow.amount(), flow.kind() == CashFlowKind.PENDING_DEBIT_RESERVATION ? flow.kind() : CashFlowKind.MESSAGE_EVIDENCE,
                    original != null && original.recurring(), flow.flexibility(), flow.category()));
        }
        List<ForecastIssue> issues = new ArrayList<>(resolution.unresolvedEvidence());
        issues.addAll(recurrence.unresolvedEvidence());
        issues.addAll(extraction.issues());
        issues.addAll(linked.issues());
        issues.addAll(occurrences.issues());
        ForecastResult result = forecastEngine.simulate(profile, requestDate, cashFlows, payments, adjustments, issues);
        cashFlows.sort(Comparator.comparing(ResolvedCashFlow::date).thenComparingInt(CashFlowForecastService::sameDayOrder)
                .thenComparing(ResolvedCashFlow::flowId));
        List<MessageEvidenceLayer.BoundOverride> applied = new ArrayList<>(linked.overrides());
        for (FinancialEvent event : occurrences.events()) {
            EventResolutionOverride selected = EventResolver.selectOverride(event, overrides);
            if (selected != null) occurrences.overrides().stream().filter(bound -> bound.override().eventId().equals(selected.eventId())
                            && bound.override().source().equals(selected.source()) && bound.override().observedAt().equals(selected.observedAt()))
                    .findFirst().ifPresent(bound -> applied.add(new MessageEvidenceLayer.BoundOverride(bound.messageId(), selected)));
        }
        return new ForecastDiagnostic(result, cashFlows, applied, extraction.notes());
    }

    private static int sameDayOrder(ResolvedCashFlow flow) {
        if (flow.direction() == EventDirection.DEBIT) return flow.kind() == CashFlowKind.HYPOTHETICAL_PAYMENT ? 1 : 0;
        return 2;
    }
}
