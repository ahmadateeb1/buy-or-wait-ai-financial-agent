package com.buyorwait.evidence;

import com.buyorwait.model.CurrencyCode;
import com.buyorwait.model.EventStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Financial assertions only; this model cannot express executable instructions or recommendations. */
public sealed interface MessageFact {
    enum Scope { NEXT_OCCURRENCE, FROM_EFFECTIVE_DATE }
    record SalaryAmount(BigDecimal amount, CurrencyCode currency, Optional<LocalDate> date, Scope scope) implements MessageFact { }
    record SalaryDate(LocalDate date) implements MessageFact { }
    record SalaryStopped() implements MessageFact { }
    record RentIncrease(BigDecimal percent) implements MessageFact { }
    record EventState(EventStatus status, Optional<LocalDate> date) implements MessageFact { }
    enum ClarificationKind {
        REGULAR_SALARY_CONFIRMED, UNCONFIRMED_INCOME, PENDING_PAYOUT, OWN_ACCOUNT_TRANSFER,
        UNPOSTED_REVERSAL, ONE_OFF_REIMBURSEMENT, SEPARATE_CARD_MINIMUMS, FOREIGN_SETTLEMENT_RATE,
        APPROVED_INVOICE_AWAITING_SETTLEMENT, ONE_OFF_ARREARS
    }
    record Clarification(ClarificationKind kind) implements MessageFact { }
}
