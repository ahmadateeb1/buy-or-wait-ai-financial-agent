package com.buyorwait.model;

public record AnalysisRequest(
        FinancialProfile financialProfile,
        PurchaseDetails purchase) {
}
