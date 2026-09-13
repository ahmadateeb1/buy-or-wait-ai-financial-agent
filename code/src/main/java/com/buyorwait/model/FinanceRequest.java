package com.buyorwait.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinanceRequest(
        String requestId,
        String userId,
        LocalDate requestDate,
        RequestType requestType,
        BigDecimal requestedAmount,
        LocalDate desiredCompletionDate,
        boolean allowsPartialPayment,
        String requestText) {
}
