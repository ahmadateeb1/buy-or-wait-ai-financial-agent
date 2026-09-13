package com.buyorwait.support;

import java.math.BigDecimal;
import java.util.Optional;

public final class MoneyUtil {
    private MoneyUtil() {
    }

    public static BigDecimal required(String raw, String file, long row, String column) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("%s row %d column %s is required".formatted(file, row, column));
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "%s row %d column %s has invalid decimal '%s'".formatted(file, row, column, raw), exception);
        }
    }

    public static Optional<BigDecimal> optional(String raw, String file, long row, String column) {
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(required(raw, file, row, column));
    }
}
