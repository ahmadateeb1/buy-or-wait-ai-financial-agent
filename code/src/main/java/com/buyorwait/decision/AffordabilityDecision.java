package com.buyorwait.decision;

import com.buyorwait.finance.*;
import com.buyorwait.model.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public record AffordabilityDecision(String requestId, BigDecimal amountSafeToPay, AffordabilityStatus status,
        PaymentMethod method, List<ProposedPayment> payments, Optional<LocalDate> earliestFullPayment,
        List<SpendingAdjustment> changes, String explanation, List<ForecastIssue> unresolvedEvidence) {
    public AffordabilityDecision {
        payments = List.copyOf(payments);
        changes = List.copyOf(changes);
        unresolvedEvidence = List.copyOf(unresolvedEvidence);
    }

    public static String decimal(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }

    public String paymentPlan() {
        return payments.isEmpty() ? "none" : payments.stream()
                .map(payment -> payment.paymentDate() + ":" + decimal(payment.amount())).collect(Collectors.joining("|"));
    }

    public String spendingChanges() {
        return changes.isEmpty() ? "none" : changes.stream().map(change -> change instanceof SpendingAdjustment.Stop
                ? "stop:" + change.originEventId()
                : "reduce_to:" + change.originEventId() + ":" + decimal(((SpendingAdjustment.ReduceTo) change).newAmount()))
                .collect(Collectors.joining("|"));
    }

    public List<String> columns() {
        return List.of(requestId, decimal(amountSafeToPay), status.csvValue(), method.csvValue(), paymentPlan(),
                earliestFullPayment.map(Object::toString).orElse(""), spendingChanges(), explanation);
    }
}
