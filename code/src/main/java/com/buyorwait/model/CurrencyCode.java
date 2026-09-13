package com.buyorwait.model;

public enum CurrencyCode implements CsvValue {
    INR, ZAR, IDR, USD, EUR;

    @Override
    public String csvValue() {
        return name();
    }
}
