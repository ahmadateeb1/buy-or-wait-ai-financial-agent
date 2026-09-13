package com.buyorwait.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public record PaymentOption(
        String paymentOptionId,
        String requestId,
        PaymentMethod paymentMethod,
        BigDecimal paymentAmount,
        int numberOfPayments,
        LocalDate firstPaymentDate,
        Optional<Integer> paymentFrequencyDays,
        BigDecimal financingFee,
        BigDecimal totalPayableAmount) {
}
