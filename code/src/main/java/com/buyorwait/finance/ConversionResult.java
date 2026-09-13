package com.buyorwait.finance;

import java.math.BigDecimal;
import java.util.Optional;

public record ConversionResult(Optional<BigDecimal> amount, Optional<ForecastIssue> issue) {
    public ConversionResult {
        amount = amount == null ? Optional.empty() : amount;
        issue = issue == null ? Optional.empty() : issue;
    }
}
