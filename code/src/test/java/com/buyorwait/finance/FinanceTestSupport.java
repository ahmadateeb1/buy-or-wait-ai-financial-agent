package com.buyorwait.finance;

import com.buyorwait.input.Dataset;
import com.buyorwait.input.DatasetIndex;
import com.buyorwait.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class FinanceTestSupport {
    private FinanceTestSupport() { }

    static FinancialProfile profile(BigDecimal balance, BigDecimal minimum) {
        return new FinancialProfile("user", CurrencyCode.INR, balance, minimum, Set.of(), Set.of("rent"), Set.of("dining"),
                Set.of("streaming"), Set.of(PaymentMethod.FULL_PAYMENT), Optional.empty());
    }

    static FinancialEvent event(String id, EventType type, String category, EventDirection direction, String amount,
            CurrencyCode currency, LocalDate settlement, EventStatus status, String linked) {
        return new FinancialEvent(id, "user", type, id, category, direction, Optional.ofNullable(amount).map(BigDecimal::new), currency,
                settlement, Optional.ofNullable(settlement), status, Optional.ofNullable(linked), Flexibility.FIXED, Optional.empty());
    }

    static CurrencyConverter converter(ExchangeRate... rates) {
        Dataset dataset = new Dataset(List.of(), List.of(), List.of(rates), List.of(), List.of(), List.of(), List.of(), List.of());
        return new CurrencyConverter(new DatasetIndex(dataset));
    }
}
