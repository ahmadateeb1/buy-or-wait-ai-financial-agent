package com.buyorwait.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRate(
        LocalDate rateDate,
        CurrencyCode fromCurrency,
        CurrencyCode toCurrency,
        BigDecimal rate) {
}
