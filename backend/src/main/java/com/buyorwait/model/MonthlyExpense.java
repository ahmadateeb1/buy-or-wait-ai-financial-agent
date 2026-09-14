package com.buyorwait.model;

import java.math.BigDecimal;

public record MonthlyExpense(
        ExpenseCategory category,
        BigDecimal amount) {
}
