package com.buyorwait.model;

import java.time.LocalDate;

public record ExchangeRateKey(LocalDate rateDate, CurrencyCode fromCurrency, CurrencyCode toCurrency) {
}
