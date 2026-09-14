package com.buyorwait.model;

import java.math.BigDecimal;

public record PurchaseDetails(
        String productName,
        BigDecimal price,
        String currency,
        boolean alreadyOwnSimilarProduct,
        UsageFrequency usageFrequency,
        int urgency) {
}
