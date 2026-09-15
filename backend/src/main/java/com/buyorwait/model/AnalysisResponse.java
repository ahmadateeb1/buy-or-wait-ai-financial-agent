package com.buyorwait.model;

import java.util.List;

public record AnalysisResponse(
        Verdict verdict,
        int confidence,
        String summary,
        List<String> reasons) {

    public AnalysisResponse {
        if (confidence < 0 || confidence > 100) {
            throw new IllegalArgumentException("Confidence must be between 0 and 100");
        }
        reasons = List.copyOf(reasons);
    }
}
