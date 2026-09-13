package com.buyorwait.model;

public enum EventType implements CsvValue {
    DEBT_PAYMENT("debt_payment"), EXPENSE("expense"), INCOME("income"),
    INVESTMENT_PURCHASE("investment_purchase"), INVESTMENT_SALE("investment_sale"),
    INVESTMENT_VALUATION("investment_valuation"), REFUND("refund"), SUBSCRIPTION("subscription");

    private final String value;
    EventType(String value) { this.value = value; }
    public String csvValue() { return value; }
}
