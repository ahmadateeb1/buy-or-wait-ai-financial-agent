package com.buyorwait.finance;

import com.buyorwait.model.EventDirection;
import com.buyorwait.model.Flexibility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public record ResolvedCashFlow(
        String flowId,
        Optional<String> originEventId,
        LocalDate date,
        EventDirection direction,
        BigDecimal amount,
        CashFlowKind kind,
        boolean recurring,
        Flexibility flexibility,
        String category) {
    public ResolvedCashFlow {
        originEventId = originEventId == null ? Optional.empty() : originEventId;
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Cash-flow amount must be non-negative");
    }
}
