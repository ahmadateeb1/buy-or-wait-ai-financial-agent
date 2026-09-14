package com.buyorwait.model;

import java.math.BigDecimal;
import java.util.List;

public record FinancialProfile(
        IncomeType incomeType,
        BigDecimal monthlyIncome,
        List<MonthlyExpense> monthlyExpenses,
        BigDecimal liquidSavings,
        BigDecimal monthlyDebtPayments) {
    public FinancialProfile {
        monthlyExpenses = List.copyOf(monthlyExpenses);
    }
}
