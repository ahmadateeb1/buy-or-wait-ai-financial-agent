package com.buyorwait.model;

public record SampleOutput(
        String amountSafeToPay,
        String affordabilityStatus,
        String recommendedPaymentMethod,
        String paymentPlan,
        String earliestDateForFullPayment,
        String spendingChangesNeeded,
        String decisionExplanation) {
}
