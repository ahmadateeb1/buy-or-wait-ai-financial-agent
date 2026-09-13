package com.buyorwait.diagnostic;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;

/** Experimental estimators used only by the sample comparison diagnostic. */
public enum RecurrenceAmountEstimator {
    CURRENT_MAX, HISTORICAL_MEAN, HISTORICAL_MEDIAN, RECENT_3_MEAN, RECENT_5_MEAN, RECENT_3_MAX;

    public String label() { return name().toLowerCase(Locale.ROOT); }

    public BigDecimal estimate(List<BigDecimal> chronologicalAmounts) {
        if (chronologicalAmounts.isEmpty()) throw new IllegalArgumentException("Recurring history must not be empty");
        return switch (this) {
            case CURRENT_MAX -> maximum(chronologicalAmounts);
            case HISTORICAL_MEAN -> mean(chronologicalAmounts);
            case HISTORICAL_MEDIAN -> median(chronologicalAmounts);
            case RECENT_3_MEAN -> mean(recent(chronologicalAmounts, 3));
            case RECENT_5_MEAN -> mean(recent(chronologicalAmounts, 5));
            case RECENT_3_MAX -> maximum(recent(chronologicalAmounts, 3));
        };
    }

    private static List<BigDecimal> recent(List<BigDecimal> amounts, int count) {
        return amounts.subList(Math.max(0, amounts.size() - count), amounts.size());
    }

    private static BigDecimal maximum(List<BigDecimal> amounts) {
        return amounts.stream().max(BigDecimal::compareTo).orElseThrow();
    }

    private static BigDecimal mean(List<BigDecimal> amounts) {
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(amounts.size()), MathContext.DECIMAL128);
    }

    private static BigDecimal median(List<BigDecimal> amounts) {
        List<BigDecimal> sorted = amounts.stream().sorted(BigDecimal::compareTo).toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle)
                : sorted.get(middle - 1).add(sorted.get(middle)).divide(BigDecimal.valueOf(2));
    }
}
