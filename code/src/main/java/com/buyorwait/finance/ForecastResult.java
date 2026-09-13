package com.buyorwait.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record ForecastResult(
        ForecastStatus status,
        BigDecimal startingBalance,
        BigDecimal endingBalance,
        BigDecimal minimumProjectedBalance,
        LocalDate minimumBalanceDate,
        Optional<LocalDate> firstViolationDate,
        List<ForecastTimelineEntry> timeline,
        List<ForecastIssue> unresolvedEvidence) {
    public ForecastResult {
        firstViolationDate = firstViolationDate == null ? Optional.empty() : firstViolationDate;
        timeline = List.copyOf(timeline);
        unresolvedEvidence = List.copyOf(unresolvedEvidence);
    }

    public boolean safe() {
        return status == ForecastStatus.SAFE;
    }
}
