package com.buyorwait.decision;

import com.buyorwait.finance.*;
import com.buyorwait.input.DatasetIndex;
import com.buyorwait.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.buyorwait.decision.AffordabilityDecision.decimal;

/** Deterministic candidate generation and validation over one already-resolved 90-day context. */
public final class AffordabilityService {
    private final DatasetIndex index;
    private final ForecastEngine engine = new ForecastEngine();

    public AffordabilityService(DatasetIndex index) { this.index = index; }

    public AffordabilityDecision decide(FinanceRequest request) {
        FinancialProfile profile = index.profileByUserId(request.userId()).orElseThrow();
        ForecastDiagnostic context = new CashFlowForecastService(index).diagnose(request, List.of(), List.of());
        return decide(request, profile, context, index.paymentOptionsByRequestId(request.requestId()));
    }

    public AffordabilityDecision decide(FinanceRequest request, FinancialProfile profile, ForecastDiagnostic context,
            List<PaymentOption> options) {
        List<ForecastIssue> issues = context.result().unresolvedEvidence();
        if (!issues.isEmpty()) return new AffordabilityDecision(request.requestId(), BigDecimal.ZERO,
                AffordabilityStatus.NOT_AFFORDABLE, PaymentMethod.NOT_RECOMMENDED, List.of(), Optional.empty(), List.of(),
                "No payment can be verified: unresolved financial evidence (" + issues.stream()
                        .map(issue -> issue.code() + issue.eventId().map(id -> ":" + id).orElse(""))
                        .distinct().collect(java.util.stream.Collectors.joining(", ")) + ").", issues);

        BigDecimal safeToday = context.result().safe()
                ? context.result().minimumProjectedBalance().subtract(profile.minimumBalanceToKeep())
                        .max(BigDecimal.ZERO).min(request.requestedAmount()) : BigDecimal.ZERO;
        if (safeToday.signum() > 0 && !simulate(request, profile, context,
                List.of(payment("capacity", request.requestDate(), safeToday)), List.of()).safe()) {
            throw new IllegalStateException("Today's capacity failed forecast validation");
        }
        Optional<LocalDate> earliest = earliestFull(request, profile, context, List.of());
        List<Candidate> candidates = candidates(request, profile, context, options, safeToday, earliest, List.of());
        if (candidates.isEmpty()) {
            for (List<SpendingAdjustment> changes : permittedChanges(profile, context)) {
                candidates.addAll(candidates(request, profile, context, options, safeToday, earliest, changes));
            }
        }
        if (candidates.isEmpty()) {
            String reason = "No eligible plan completes by " + request.desiredCompletionDate() + " while keeping "
                    + profile.homeCurrency() + " " + decimal(profile.minimumBalanceToKeep()) + " throughout the 90-day forecast.";
            if (!context.result().safe()) reason += " The baseline first falls below the minimum on "
                    + context.result().firstViolationDate().orElseThrow() + ".";
            if (earliest.isPresent()) reason += " A single full payment is financially safe on " + earliest.get()
                    + "; the deadline or permitted methods prevent recommending it.";
            return new AffordabilityDecision(request.requestId(), safeToday,
                    earliest.filter(date -> date.isAfter(request.requestDate())).isPresent()
                            ? AffordabilityStatus.AFFORDABLE_LATER : AffordabilityStatus.NOT_AFFORDABLE,
                    PaymentMethod.NOT_RECOMMENDED, List.of(), earliest, List.of(), reason, issues);
        }
        Candidate selected = candidates.stream().min(CANDIDATE_ORDER).orElseThrow();
        AffordabilityStatus status = selected.changes().isEmpty() && selected.method() == PaymentMethod.FULL_PAYMENT
                && selected.payments().getFirst().paymentDate().equals(request.requestDate()) ? AffordabilityStatus.AFFORDABLE_NOW
                : selected.method() == PaymentMethod.WAIT && selected.changes().isEmpty() ? AffordabilityStatus.AFFORDABLE_LATER
                : AffordabilityStatus.AFFORDABLE_WITH_PLAN;
        String explanation = "Use " + selected.method().csvValue() + " for " + profile.homeCurrency() + " "
                + decimal(selected.cost()) + ", completing on " + selected.payments().getLast().paymentDate()
                + ". Projected minimum " + decimal(selected.forecast().minimumProjectedBalance()) + " on "
                + selected.forecast().minimumBalanceDate() + " stays at or above " + decimal(profile.minimumBalanceToKeep()) + "."
                + (selected.changes().isEmpty() ? " No spending changes required." : " Requires the listed permitted spending changes.");
        return new AffordabilityDecision(request.requestId(), safeToday, status, selected.method(), selected.payments(),
                earliest, selected.changes(), explanation, issues);
    }

    private Optional<LocalDate> earliestFull(FinanceRequest request, FinancialProfile profile, ForecastDiagnostic context,
            List<SpendingAdjustment> changes) {
        if (!simulate(request, profile, context, List.of(), changes).safe()) return Optional.empty();
        for (int day = 0; day < 90; day++) {
            LocalDate date = request.requestDate().plusDays(day);
            if (simulate(request, profile, context, List.of(payment("full-capacity", date, request.requestedAmount())), changes).safe()) {
                return Optional.of(date);
            }
        }
        return Optional.empty();
    }

    private List<Candidate> candidates(FinanceRequest request, FinancialProfile profile, ForecastDiagnostic context,
            List<PaymentOption> options, BigDecimal safeToday, Optional<LocalDate> earliest, List<SpendingAdjustment> changes) {
        List<Candidate> result = new ArrayList<>();
        for (PaymentOption option : options) {
            if (!option.requestId().equals(request.requestId()) || !profile.paymentMethodsUserWillConsider().contains(option.paymentMethod())) continue;
            if (option.paymentAmount().signum() <= 0 || option.numberOfPayments() < 1
                    || option.paymentAmount().multiply(BigDecimal.valueOf(option.numberOfPayments())).compareTo(option.totalPayableAmount()) != 0
                    || request.requestedAmount().add(option.financingFee()).compareTo(option.totalPayableAmount()) != 0) continue;
            if (option.paymentMethod() != PaymentMethod.FULL_PAYMENT && option.paymentMethod() != PaymentMethod.INSTALLMENTS) continue;
            if (option.paymentMethod() == PaymentMethod.FULL_PAYMENT && (option.numberOfPayments() != 1
                    || option.paymentAmount().compareTo(request.requestedAmount()) != 0)) continue;
            if (option.paymentMethod() == PaymentMethod.INSTALLMENTS
                    && (profile.maxInstallmentMonths().isEmpty() || option.paymentFrequencyDays().isEmpty())) continue;
            List<ProposedPayment> payments = new ArrayList<>();
            for (int i = 0; i < option.numberOfPayments(); i++) {
                payments.add(payment(option.paymentOptionId() + ":" + i,
                        option.firstPaymentDate().plusDays((long) i * option.paymentFrequencyDays().orElse(0)), option.paymentAmount()));
            }
            if (option.paymentMethod() == PaymentMethod.INSTALLMENTS && payments.getLast().paymentDate()
                    .isAfter(option.firstPaymentDate().plusMonths(profile.maxInstallmentMonths().orElseThrow()))) continue;
            validateCandidate(result, request, profile, context, option.paymentMethod(), payments, changes, option.paymentOptionId());
        }
        if (profile.paymentMethodsUserWillConsider().contains(PaymentMethod.FULL_PAYMENT)) {
            Optional<LocalDate> date = changes.isEmpty() ? earliest : earliestFull(request, profile, context, changes);
            if (date.isPresent() && date.get().isAfter(request.requestDate())) {
                validateCandidate(result, request, profile, context, PaymentMethod.WAIT,
                        List.of(payment("wait", date.get(), request.requestedAmount())), changes, "~wait");
            }
        }
        if (request.allowsPartialPayment() && profile.paymentMethodsUserWillConsider().contains(PaymentMethod.PARTIAL_PAYMENT)
                && safeToday.signum() > 0 && safeToday.compareTo(request.requestedAmount()) < 0 && earliest.isPresent()) {
            validateCandidate(result, request, profile, context, PaymentMethod.PARTIAL_PAYMENT,
                    List.of(payment("partial:first", request.requestDate(), safeToday),
                            payment("partial:remainder", earliest.get(), request.requestedAmount().subtract(safeToday))), changes, "~partial");
        }
        return result;
    }

    private void validateCandidate(List<Candidate> result, FinanceRequest request, FinancialProfile profile,
            ForecastDiagnostic context, PaymentMethod method, List<ProposedPayment> payments,
            List<SpendingAdjustment> changes, String optionId) {
        LocalDate horizon = request.requestDate().plusDays(89);
        if (payments.stream().anyMatch(payment -> payment.paymentDate().isBefore(request.requestDate())
                || payment.paymentDate().isAfter(horizon) || payment.paymentDate().isAfter(request.desiredCompletionDate()))) return;
        ForecastResult forecast = simulate(request, profile, context, payments, changes);
        if (forecast.safe()) result.add(new Candidate(method, payments, changes, optionId, forecast));
    }

    private ForecastResult simulate(FinanceRequest request, FinancialProfile profile, ForecastDiagnostic context,
            List<ProposedPayment> payments, List<SpendingAdjustment> changes) {
        return engine.simulate(profile, request.requestDate(), context.cashFlows(), payments, changes, context.result().unresolvedEvidence());
    }

    private List<List<SpendingAdjustment>> permittedChanges(FinancialProfile profile, ForecastDiagnostic context) {
        Map<String, List<ResolvedCashFlow>> grouped = new TreeMap<>();
        context.cashFlows().stream().filter(flow -> flow.recurring() && flow.direction() == EventDirection.DEBIT)
                .forEach(flow -> flow.originEventId().ifPresent(origin -> grouped.computeIfAbsent(origin, ignored -> new ArrayList<>()).add(flow)));
        List<List<SpendingAdjustment>> choices = new ArrayList<>();
        CurrencyConverter converter = new CurrencyConverter(index);
        grouped.forEach((origin, flows) -> {
            FinancialEvent event = index.eventById(origin).orElse(null);
            if (event == null || profile.expenseCategoriesToProtect().contains(event.category())) return;
            List<SpendingAdjustment> actions = new ArrayList<>();
            if ((event.flexibility() == Flexibility.STOPPABLE || event.flexibility() == Flexibility.REDUCIBLE_OR_STOPPABLE)
                    && profile.expenseCategoriesUserIsWillingToStop().contains(event.category())) actions.add(new SpendingAdjustment.Stop(origin));
            if ((event.flexibility() == Flexibility.REDUCIBLE || event.flexibility() == Flexibility.REDUCIBLE_OR_STOPPABLE)
                    && profile.expenseCategoriesUserIsWillingToReduce().contains(event.category()) && event.minimumAllowedAmount().isPresent()
                    && event.settlementDate().isPresent()) {
                ConversionResult floor = converter.convert(event.minimumAllowedAmount().get(), event.currency(), profile.homeCurrency(),
                        event.settlementDate().get(), origin);
                floor.amount().filter(amount -> amount.signum() >= 0 && flows.stream().allMatch(flow -> amount.compareTo(flow.amount()) < 0))
                        .ifPresent(amount -> actions.add(new SpendingAdjustment.ReduceTo(origin, amount)));
            }
            if (!actions.isEmpty()) choices.add(actions);
        });
        List<List<SpendingAdjustment>> combinations = new ArrayList<>();
        enumerateChanges(choices, 0, new ArrayList<>(), combinations);
        return combinations;
    }

    private static void enumerateChanges(List<List<SpendingAdjustment>> choices, int offset,
            List<SpendingAdjustment> current, List<List<SpendingAdjustment>> output) {
        if (!current.isEmpty()) output.add(List.copyOf(current));
        if (current.size() == 3) return;
        for (int i = offset; i < choices.size(); i++) for (SpendingAdjustment action : choices.get(i)) {
            current.add(action);
            enumerateChanges(choices, i + 1, current, output);
            current.removeLast();
        }
    }

    private static ProposedPayment payment(String id, LocalDate date, BigDecimal amount) { return new ProposedPayment(id, date, amount); }

    private record Candidate(PaymentMethod method, List<ProposedPayment> payments, List<SpendingAdjustment> changes,
            String optionId, ForecastResult forecast) {
        BigDecimal cost() { return payments.stream().map(ProposedPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    }

    // Deadline is a hard eligibility check. Equal-ranked candidates retain deterministic generation order.
    private static final Comparator<Candidate> CANDIDATE_ORDER = Comparator
            .comparing((Candidate candidate) -> !candidate.changes().isEmpty())
            .thenComparing(Candidate::cost, BigDecimal::compareTo)
            .thenComparing(candidate -> candidate.payments().getFirst().paymentDate())
            .thenComparingInt(candidate -> candidate.payments().size())
            .thenComparing(Candidate::optionId);
}
