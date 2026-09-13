package com.buyorwait.finance;

import com.buyorwait.input.DatasetIndex;
import com.buyorwait.model.CurrencyCode;
import com.buyorwait.model.ExchangeRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public final class CurrencyConverter {
    private final DatasetIndex index;

    public CurrencyConverter(DatasetIndex index) {
        this.index = index;
    }

    public ConversionResult convert(BigDecimal amount, CurrencyCode from, CurrencyCode to, LocalDate settlementDate, String eventId) {
        if (from == to) return new ConversionResult(Optional.of(amount), Optional.empty());
        if (settlementDate == null) {
            return unresolved("missing_settlement_date", "Cannot convert foreign-currency event without a settlement date", eventId);
        }
        Optional<ExchangeRate> rate = index.exchangeRate(settlementDate, from, to);
        if (rate.isEmpty()) {
            return unresolved("missing_exchange_rate", "No exchange rate for %s to %s on %s"
                    .formatted(from, to, settlementDate), eventId);
        }
        return new ConversionResult(Optional.of(amount.multiply(rate.get().rate())), Optional.empty());
    }

    private static ConversionResult unresolved(String code, String message, String eventId) {
        return new ConversionResult(Optional.empty(), Optional.of(ForecastIssue.forEvent(code, message, eventId)));
    }
}
