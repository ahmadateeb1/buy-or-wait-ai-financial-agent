package com.buyorwait.model;

public enum PaymentMethod implements CsvValue {
    FULL_PAYMENT("full_payment"), PARTIAL_PAYMENT("partial_payment"), INSTALLMENTS("installments"),
    WAIT("wait"), NOT_RECOMMENDED("not_recommended");

    private final String value;
    PaymentMethod(String value) { this.value = value; }
    public String csvValue() { return value; }
}
