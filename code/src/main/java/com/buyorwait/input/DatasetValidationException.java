package com.buyorwait.input;

import java.util.List;

public final class DatasetValidationException extends IllegalArgumentException {
    private final List<String> violations;

    public DatasetValidationException(List<String> violations) {
        super("Dataset validation failed:\n - " + String.join("\n - ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() { return violations; }
}
