package com.buyorwait.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FinancialProfileTest {

    @Test
    void createsMonthlyExpense() {
        MonthlyExpense expense = new MonthlyExpense(
                ExpenseCategory.GROCERIES,
                new BigDecimal("450.75"));

        assertEquals(ExpenseCategory.GROCERIES, expense.category());
        assertEquals(new BigDecimal("450.75"), expense.amount());
    }

    @Test
    void createsFinancialProfileWithMultipleMonthlyExpenses() {
        List<MonthlyExpense> expenses = List.of(
                new MonthlyExpense(ExpenseCategory.HOUSING, new BigDecimal("1200")),
                new MonthlyExpense(ExpenseCategory.UTILITIES, new BigDecimal("180.50")));

        FinancialProfile profile = new FinancialProfile(
                IncomeType.SALARIED,
                new BigDecimal("5000"),
                expenses,
                new BigDecimal("10000"),
                new BigDecimal("350"));

        assertEquals(2, profile.monthlyExpenses().size());
        assertEquals(expenses, profile.monthlyExpenses());
    }

    @Test
    void copiesMonthlyExpensesIntoAnUnmodifiableList() {
        List<MonthlyExpense> expenses = new ArrayList<>();
        expenses.add(new MonthlyExpense(ExpenseCategory.PHONE, new BigDecimal("60")));

        FinancialProfile profile = new FinancialProfile(
                IncomeType.FREELANCE,
                new BigDecimal("4200"),
                expenses,
                new BigDecimal("7500"),
                new BigDecimal("200"));

        expenses.add(new MonthlyExpense(ExpenseCategory.INTERNET, new BigDecimal("80")));

        assertEquals(1, profile.monthlyExpenses().size());
        assertThrows(UnsupportedOperationException.class,
                () -> profile.monthlyExpenses().add(
                        new MonthlyExpense(ExpenseCategory.OTHER, BigDecimal.ONE)));
    }
}
