package com.buyorwait.model;

import java.util.Arrays;

public final class EnumParser {
    private EnumParser() {
    }

    public static <E extends Enum<E> & CsvValue> E parse(
            Class<E> type, String value, String file, long row, String column) {
        return Arrays.stream(type.getEnumConstants())
                .filter(candidate -> candidate.csvValue().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "%s row %d column %s has unsupported value '%s' for %s"
                                .formatted(file, row, column, value, type.getSimpleName())));
    }
}
