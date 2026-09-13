package com.buyorwait.model;

public enum EventStatus implements CsvValue {
    SETTLED("settled"), PENDING("pending"), SCHEDULED("scheduled"), FAILED("failed"),
    CANCELLED("cancelled"), UNREALIZED("unrealized");

    private final String value;
    EventStatus(String value) { this.value = value; }
    public String csvValue() { return value; }
}
