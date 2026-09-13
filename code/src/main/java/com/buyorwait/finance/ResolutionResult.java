package com.buyorwait.finance;

import java.util.List;

public record ResolutionResult(List<ResolvedCashFlow> cashFlows, List<ForecastIssue> unresolvedEvidence) {
    public ResolutionResult {
        cashFlows = List.copyOf(cashFlows);
        unresolvedEvidence = List.copyOf(unresolvedEvidence);
    }
}
