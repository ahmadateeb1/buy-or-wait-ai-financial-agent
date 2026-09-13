package com.buyorwait.model;

public enum EventDirection implements CsvValue {
    CREDIT("credit"), DEBIT("debit"), NON_CASH("non_cash");

    private final String value;
    EventDirection(String value) { this.value = value; }
    public String csvValue() { return value; }
}
