package com.buyorwait.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProposedPayment(String paymentId, LocalDate paymentDate, BigDecimal amount) {
    public ProposedPayment {
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Payment amount must be non-negative");
    }
}
