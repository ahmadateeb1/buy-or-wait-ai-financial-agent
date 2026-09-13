package com.buyorwait.finance;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;

public record RecurrenceAnalysisResult(List<ResolvedCashFlow> cashFlows, List<ForecastIssue> unresolvedEvidence,
        Map<String, List<BigDecimal>> historicalAmountsByOriginEventId) {
    public RecurrenceAnalysisResult(List<ResolvedCashFlow> cashFlows, List<ForecastIssue> unresolvedEvidence) {
        this(cashFlows, unresolvedEvidence, Map.of());
    }

    public RecurrenceAnalysisResult {
        cashFlows = List.copyOf(cashFlows);
        unresolvedEvidence = List.copyOf(unresolvedEvidence);
        Map<String, List<BigDecimal>> copy = new HashMap<>();
        historicalAmountsByOriginEventId.forEach((origin, amounts) -> copy.put(origin, List.copyOf(amounts)));
        historicalAmountsByOriginEventId = Map.copyOf(copy);
    }
}
