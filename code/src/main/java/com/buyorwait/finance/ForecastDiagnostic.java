package com.buyorwait.finance;

import java.util.List;
import com.buyorwait.evidence.MessageEvidenceLayer.BoundOverride;

public record ForecastDiagnostic(ForecastResult result, List<ResolvedCashFlow> cashFlows,
        List<BoundOverride> messageOverrides, List<String> evidenceNotes) {
    public ForecastDiagnostic(ForecastResult result, List<ResolvedCashFlow> cashFlows) {
        this(result, cashFlows, List.of(), List.of());
    }
    public ForecastDiagnostic {
        cashFlows = List.copyOf(cashFlows);
        messageOverrides = List.copyOf(messageOverrides);
        evidenceNotes = List.copyOf(evidenceNotes);
    }
}
