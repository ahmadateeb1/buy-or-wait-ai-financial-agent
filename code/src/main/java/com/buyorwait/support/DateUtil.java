package com.buyorwait.support;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public final class DateUtil {
    private DateUtil() {
    }

    public static LocalDate requiredDate(String raw, String file, long row, String column) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("%s row %d column %s is required".formatted(file, row, column));
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "%s row %d column %s has invalid ISO date '%s'".formatted(file, row, column, raw), exception);
        }
    }

    public static Optional<LocalDate> optionalDate(String raw, String file, long row, String column) {
        return raw == null || raw.isBlank() ? Optional.empty() : Optional.of(requiredDate(raw, file, row, column));
    }

    public static Instant requiredInstant(String raw, String file, long row, String column) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("%s row %d column %s is required".formatted(file, row, column));
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "%s row %d column %s has invalid ISO instant '%s'".formatted(file, row, column, raw), exception);
        }
    }
}
