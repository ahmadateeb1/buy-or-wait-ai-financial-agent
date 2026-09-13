package com.buyorwait.finance;

import com.buyorwait.model.EventDirection;
import com.buyorwait.model.FinancialProfile;
import com.buyorwait.model.Flexibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class ForecastEngine {
    public ForecastResult simulate(FinancialProfile profile, LocalDate requestDate, List<ResolvedCashFlow> cashFlows,
            List<ProposedPayment> proposedPayments, List<SpendingAdjustment> adjustments) {
        return simulate(profile, requestDate, cashFlows, proposedPayments, adjustments, List.of());
    }

    public ForecastResult simulate(FinancialProfile profile, LocalDate requestDate, List<ResolvedCashFlow> cashFlows,
            List<ProposedPayment> proposedPayments, List<SpendingAdjustment> adjustments, List<ForecastIssue> initialIssues) {
        List<ForecastIssue> issues = new ArrayList<>(initialIssues);
        Map<String, SpendingAdjustment> adjustmentByEvent = validateAdjustments(profile, cashFlows, adjustments, issues);
        LocalDate end = requestDate.plusDays(89);
        List<ResolvedCashFlow> flows = new ArrayList<>();
        for (ResolvedCashFlow flow : cashFlows) {
            if (flow.date().isBefore(requestDate) || flow.date().isAfter(end)) continue;
            applyAdjustment(flow, adjustmentByEvent).ifPresent(flows::add);
        }
        for (ProposedPayment payment : proposedPayments) {
            if (payment.paymentDate().isBefore(requestDate) || payment.paymentDate().isAfter(end)) {
                issues.add(new ForecastIssue("payment_outside_horizon", "Hypothetical payment is outside the 90-day forecast", Optional.empty()));
                continue;
            }
            flows.add(new ResolvedCashFlow(payment.paymentId(), Optional.empty(), payment.paymentDate(), EventDirection.DEBIT,
                    payment.amount(), CashFlowKind.HYPOTHETICAL_PAYMENT, false, Flexibility.FIXED, "hypothetical_payment"));
        }
        flows.sort(Comparator.comparing(ResolvedCashFlow::date).thenComparingInt(ForecastEngine::sameDayOrder)
                .thenComparing(ResolvedCashFlow::flowId));

        BigDecimal balance = profile.currentAvailableBalance();
        BigDecimal minimum = balance;
        LocalDate minimumDate = requestDate;
        Optional<LocalDate> firstViolation = balance.compareTo(profile.minimumBalanceToKeep()) < 0
                ? Optional.of(requestDate) : Optional.empty();
        List<ForecastTimelineEntry> timeline = new ArrayList<>();
        for (ResolvedCashFlow flow : flows) {
            balance = flow.direction() == EventDirection.DEBIT ? balance.subtract(flow.amount()) : balance.add(flow.amount());
            if (balance.compareTo(minimum) < 0) {
                minimum = balance;
                minimumDate = flow.date();
            }
            if (firstViolation.isEmpty() && balance.compareTo(profile.minimumBalanceToKeep()) < 0) {
                firstViolation = Optional.of(flow.date());
            }
            timeline.add(new ForecastTimelineEntry(flow.date(), flow.flowId(), flow.kind(), flow.direction(), flow.amount(), balance));
        }
        ForecastStatus status = !issues.isEmpty() ? ForecastStatus.UNRESOLVED
                : firstViolation.isPresent() ? ForecastStatus.UNSAFE : ForecastStatus.SAFE;
        return new ForecastResult(status, profile.currentAvailableBalance(), balance, minimum, minimumDate,
                firstViolation, timeline, issues);
    }

    private static int sameDayOrder(ResolvedCashFlow flow) {
        if (flow.direction() == EventDirection.DEBIT) return flow.kind() == CashFlowKind.HYPOTHETICAL_PAYMENT ? 1 : 0;
        return 2;
    }

    private static Map<String, SpendingAdjustment> validateAdjustments(FinancialProfile profile, List<ResolvedCashFlow> flows,
            List<SpendingAdjustment> adjustments, List<ForecastIssue> issues) {
        Map<String, SpendingAdjustment> result = new HashMap<>();
        for (SpendingAdjustment adjustment : adjustments) {
            List<ResolvedCashFlow> targets = flows.stream().filter(flow -> flow.originEventId().filter(adjustment.originEventId()::equals).isPresent()).toList();
            if (targets.isEmpty() || targets.stream().anyMatch(flow -> !flow.recurring() || flow.direction() != EventDirection.DEBIT)) {
                issues.add(ForecastIssue.forEvent("invalid_adjustment", "Adjustment must target a recurring debit", adjustment.originEventId()));
                continue;
            }
            ResolvedCashFlow target = targets.getFirst();
            boolean permitted = adjustment instanceof SpendingAdjustment.Stop
                    ? (target.flexibility() == Flexibility.STOPPABLE || target.flexibility() == Flexibility.REDUCIBLE_OR_STOPPABLE)
                    && profile.expenseCategoriesUserIsWillingToStop().contains(target.category())
                    : (target.flexibility() == Flexibility.REDUCIBLE || target.flexibility() == Flexibility.REDUCIBLE_OR_STOPPABLE)
                    && profile.expenseCategoriesUserIsWillingToReduce().contains(target.category());
            if (!permitted || result.putIfAbsent(adjustment.originEventId(), adjustment) != null) {
                issues.add(ForecastIssue.forEvent("invalid_adjustment", "Adjustment is not permitted for its recurring expense", adjustment.originEventId()));
            }
        }
        return result;
    }

    private static Optional<ResolvedCashFlow> applyAdjustment(ResolvedCashFlow flow, Map<String, SpendingAdjustment> adjustments) {
        Optional<String> origin = flow.originEventId();
        if (origin.isEmpty()) return Optional.of(flow);
        SpendingAdjustment adjustment = adjustments.get(origin.get());
        if (adjustment == null) return Optional.of(flow);
        if (adjustment instanceof SpendingAdjustment.Stop) return Optional.empty();
        SpendingAdjustment.ReduceTo reduction = (SpendingAdjustment.ReduceTo) adjustment;
        return Optional.of(new ResolvedCashFlow(flow.flowId(), flow.originEventId(), flow.date(), flow.direction(), reduction.newAmount(),
                flow.kind(), flow.recurring(), flow.flexibility(), flow.category()));
    }
}
