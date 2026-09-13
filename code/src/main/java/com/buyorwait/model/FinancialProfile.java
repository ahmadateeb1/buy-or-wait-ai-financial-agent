package com.buyorwait.model;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

public record FinancialProfile(
        String userId,
        CurrencyCode homeCurrency,
        BigDecimal currentAvailableBalance,
        BigDecimal minimumBalanceToKeep,
        Set<String> financialPriorities,
        Set<String> expenseCategoriesToProtect,
        Set<String> expenseCategoriesUserIsWillingToReduce,
        Set<String> expenseCategoriesUserIsWillingToStop,
        Set<PaymentMethod> paymentMethodsUserWillConsider,
        Optional<Integer> maxInstallmentMonths) {
    public FinancialProfile {
        financialPriorities = Set.copyOf(financialPriorities);
        expenseCategoriesToProtect = Set.copyOf(expenseCategoriesToProtect);
        expenseCategoriesUserIsWillingToReduce = Set.copyOf(expenseCategoriesUserIsWillingToReduce);
        expenseCategoriesUserIsWillingToStop = Set.copyOf(expenseCategoriesUserIsWillingToStop);
        paymentMethodsUserWillConsider = Set.copyOf(paymentMethodsUserWillConsider);
    }
}
