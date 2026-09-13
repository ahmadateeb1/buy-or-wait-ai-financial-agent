package com.buyorwait.model;

public enum Flexibility implements CsvValue {
    FIXED("fixed"), REDUCIBLE("reducible"), STOPPABLE("stoppable"),
    REDUCIBLE_OR_STOPPABLE("reducible_or_stoppable");

    private final String value;
    Flexibility(String value) { this.value = value; }
    public String csvValue() { return value; }
}
