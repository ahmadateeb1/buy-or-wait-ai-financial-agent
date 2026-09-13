package com.buyorwait.model;

public enum RequestType implements CsvValue {
    PURCHASE("purchase"), TRAVEL("travel"), EDUCATION("education"), FAMILY_TRANSFER("family_transfer"),
    DEBT_REPAYMENT("debt_repayment"), INVESTMENT("investment"), HOUSING("housing"),
    EMERGENCY_EXPENSE("emergency_expense"), OTHER("other");

    private final String value;
    RequestType(String value) { this.value = value; }
    public String csvValue() { return value; }
}
