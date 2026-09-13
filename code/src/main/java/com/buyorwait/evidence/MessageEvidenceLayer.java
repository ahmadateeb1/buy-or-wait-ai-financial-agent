package com.buyorwait.evidence;

import com.buyorwait.finance.*;
import com.buyorwait.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.util.*;
import static com.buyorwait.evidence.MessageFact.*;

/** Binds typed facts to existing events or particular projected occurrences, never to payment decisions. */
public final class MessageEvidenceLayer {
    private final MessageEvidenceParser parser;

    public MessageEvidenceLayer(MessageEvidenceParser parser) { this.parser = parser; }

    public record Extraction(List<MessageEvidence> evidence, List<ForecastIssue> issues, List<String> notes) {
        public Extraction { evidence = List.copyOf(evidence); issues = List.copyOf(issues); notes = List.copyOf(notes); }
    }
    public record BoundOverride(String messageId, EventResolutionOverride override) { }
    public record Binding(List<FinancialEvent> events, List<BoundOverride> overrides, List<ForecastIssue> issues) {
        public Binding { events = List.copyOf(events); overrides = List.copyOf(overrides); issues = List.copyOf(issues); }
        public List<EventResolutionOverride> values() { return overrides.stream().map(BoundOverride::override).toList(); }
    }

    public Extraction extract(String userId, LocalDate requestDate, Optional<String> requestId, List<FinancialMessage> messages) {
        List<MessageEvidence> evidence = new ArrayList<>();
        List<ForecastIssue> issues = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (FinancialMessage message : messages.stream().sorted(Comparator.comparing(FinancialMessage::sentAt)
                .thenComparing(FinancialMessage::messageId)).toList()) {
            if (!message.userId().equals(userId) || message.sentAt().atZone(ZoneOffset.UTC).toLocalDate().isAfter(requestDate)) continue;
            if (message.requestId().isPresent() && !message.requestId().equals(requestId)) continue;
            try {
                List<MessageFact> facts = parser.parse(message);
                if (facts.isEmpty()) notes.add(message.messageId() + ": no supported financial clause");
                for (MessageFact fact : facts) {
                    evidence.add(new MessageEvidence(message, fact));
                    if (fact instanceof Clarification clarification) {
                        notes.add(message.messageId() + ": " + clarification.kind() + " (no new cash implied)");
                    }
                }
            } catch (IllegalArgumentException | DateTimeException exception) {
                issues.add(new ForecastIssue("invalid_message_fact", "Invalid amount, currency or date in " + message.messageId(), Optional.empty()));
            }
        }
        return new Extraction(evidence, issues, notes);
    }

    public Binding bindLinked(List<FinancialEvent> events, Extraction extraction) {
        List<BoundOverride> overrides = new ArrayList<>();
        List<ForecastIssue> issues = new ArrayList<>();
        for (MessageEvidence evidence : extraction.evidence()) {
            if (!(evidence.fact() instanceof EventState state)) continue;
            Optional<FinancialEvent> target = events.stream().filter(event -> evidence.message().relatedEventId()
                    .filter(event.eventId()::equals).isPresent() && event.userId().equals(evidence.message().userId())).findFirst();
            if (target.isEmpty()) {
                issues.add(issue(evidence, "Missing linked event"));
                continue;
            }
            FinancialEvent event = target.get();
            boolean valid = switch (state.status()) {
                case PENDING -> event.direction() == EventDirection.CREDIT;
                case FAILED -> event.direction() == EventDirection.DEBIT;
                case UNREALIZED -> event.direction() == EventDirection.NON_CASH;
                case SETTLED -> event.direction() != EventDirection.NON_CASH;
                default -> false;
            };
            if (!valid) { issues.add(issue(evidence, "Linked status is incompatible with the event's cash direction")); continue; }
            overrides.add(bound(evidence, event, Optional.of(state.status()), Optional.empty(), state.date(), Optional.empty()));
        }
        return new Binding(events, overrides, issues);
    }

    public Set<String> nonRecurringEventIds(Extraction extraction) {
        Set<String> result = new HashSet<>();
        for (MessageEvidence evidence : extraction.evidence()) {
            if (evidence.fact() instanceof Clarification c && c.kind() == ClarificationKind.ONE_OFF_REIMBURSEMENT) {
                evidence.message().relatedEventId().ifPresent(result::add);
            }
        }
        return Set.copyOf(result);
    }

    public Binding bindOccurrences(FinancialProfile profile, LocalDate requestDate, List<FinancialEvent> input,
            Set<String> projectedIds, Extraction extraction) {
        List<FinancialEvent> events = new ArrayList<>(input);
        List<BoundOverride> overrides = new ArrayList<>();
        List<ForecastIssue> issues = new ArrayList<>();
        for (MessageEvidence evidence : extraction.evidence()) {
            MessageFact fact = evidence.fact();
            if (fact instanceof SalaryAmount salary) {
                List<FinancialEvent> salaries = candidates(events, requestDate, "salary", EventDirection.CREDIT);
                // An explicit complete amount/date can establish one confirmed occurrence without inferred recurrence.
                if (salaries.isEmpty() && salary.date().isPresent() && !salary.date().get().isBefore(requestDate)) {
                    LocalDate date = salary.date().get();
                    FinancialEvent confirmed = new FinancialEvent("message-salary:" + profile.userId() + ":" + date,
                            profile.userId(), EventType.INCOME, "Message-confirmed salary", "salary", EventDirection.CREDIT,
                            Optional.of(salary.amount()), salary.currency(), date, Optional.of(date), EventStatus.SCHEDULED,
                            Optional.empty(), Flexibility.FIXED, Optional.empty());
                    events.add(confirmed);
                    salaries = List.of(confirmed);
                }
                if (salaries.isEmpty()) { issues.add(issue(evidence, "No next salary date can be established")); continue; }
                if (salary.date().filter(date -> date.isBefore(requestDate)).isPresent() && salary.scope() == Scope.NEXT_OCCURRENCE) continue;
                List<FinancialEvent> targets;
                if (salary.scope() == Scope.FROM_EFFECTIVE_DATE) {
                    LocalDate effective = salary.date().orElse(requestDate);
                    targets = salaries.stream().filter(event -> !event.settlementDate().orElseThrow().isBefore(effective)).toList();
                } else {
                    // Prefer a supplied occurrence on the stated date; otherwise amend the next inferred/scheduled one.
                    List<FinancialEvent> exact = salaries.stream().filter(event -> salary.date().equals(event.settlementDate())).toList();
                    targets = next(exact.isEmpty() ? salaries : exact, evidence, issues);
                }
                for (FinancialEvent event : targets) {
                    Optional<LocalDate> date = salary.scope() == Scope.NEXT_OCCURRENCE ? salary.date() : Optional.empty();
                    overrides.add(bound(evidence, event, Optional.of(EventStatus.SCHEDULED), Optional.of(salary.amount()), date,
                            Optional.of(salary.currency())));
                }
            } else if (fact instanceof SalaryDate delay) {
                if (delay.date().isBefore(requestDate)) continue;
                for (FinancialEvent event : next(candidates(events, requestDate, "salary", EventDirection.CREDIT), evidence, issues)) {
                    overrides.add(bound(evidence, event, Optional.empty(), Optional.empty(), Optional.of(delay.date()), Optional.empty()));
                }
            } else if (fact instanceof SalaryStopped) {
                for (FinancialEvent event : candidates(events, requestDate, "salary", EventDirection.CREDIT)) {
                    // An ended contract invalidates inferred regular pay, not a separately supplied final settlement.
                    if (projectedIds.contains(event.eventId())) overrides.add(bound(evidence, event, Optional.of(EventStatus.CANCELLED),
                            Optional.empty(), Optional.empty(), Optional.empty()));
                }
            } else if (fact instanceof RentIncrease rent) {
                for (FinancialEvent event : candidates(events, requestDate, "rent", EventDirection.DEBIT)) {
                    if (event.amount().isEmpty()) { issues.add(issue(evidence, "Rent amendment has no resolved base amount")); continue; }
                    BigDecimal amended = event.amount().get().multiply(BigDecimal.ONE.add(rent.percent().movePointLeft(2)));
                    overrides.add(bound(evidence, event, Optional.empty(), Optional.of(amended), Optional.empty(), Optional.of(event.currency())));
                }
            }
        }
        return new Binding(events, overrides, issues);
    }

    private static List<FinancialEvent> candidates(List<FinancialEvent> events, LocalDate start, String category, EventDirection direction) {
        return events.stream().filter(event -> event.category().equals(category) && event.direction() == direction
                && event.status() != EventStatus.CANCELLED && event.status() != EventStatus.FAILED
                && event.settlementDate().filter(date -> !date.isBefore(start)).isPresent())
                .sorted(Comparator.comparing((FinancialEvent event) -> event.settlementDate().orElseThrow())
                        .thenComparing(FinancialEvent::eventId)).toList();
    }

    private static List<FinancialEvent> next(List<FinancialEvent> candidates, MessageEvidence evidence, List<ForecastIssue> issues) {
        if (candidates.isEmpty()) { issues.add(issue(evidence, "No next occurrence is available")); return List.of(); }
        LocalDate first = candidates.getFirst().settlementDate().orElseThrow();
        if (candidates.stream().filter(event -> event.settlementDate().orElseThrow().equals(first)).count() != 1) {
            issues.add(issue(evidence, "Ambiguous next occurrence; multiple events share its date")); return List.of();
        }
        return List.of(candidates.getFirst());
    }

    private static BoundOverride bound(MessageEvidence evidence, FinancialEvent event, Optional<EventStatus> status,
            Optional<BigDecimal> amount, Optional<LocalDate> date, Optional<CurrencyCode> currency) {
        return new BoundOverride(evidence.message().messageId(), new EventResolutionOverride(event.eventId(),
                evidence.message().sourceType().csvValue(), evidence.message().sentAt(), true, status, amount, date, currency));
    }

    private static ForecastIssue issue(MessageEvidence evidence, String detail) {
        return new ForecastIssue("unresolved_message_evidence", evidence.message().messageId() + ": " + detail,
                evidence.message().relatedEventId());
    }
}
