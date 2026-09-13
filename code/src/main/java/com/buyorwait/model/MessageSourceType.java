package com.buyorwait.model;

public enum MessageSourceType implements CsvValue {
    EMPLOYER("employer"), SERVICE_PROVIDER("service_provider"), MERCHANT("merchant"),
    FINANCIAL_SERVICE("financial_service"), BANK("bank");

    private final String value;
    MessageSourceType(String value) { this.value = value; }
    public String csvValue() { return value; }
}
