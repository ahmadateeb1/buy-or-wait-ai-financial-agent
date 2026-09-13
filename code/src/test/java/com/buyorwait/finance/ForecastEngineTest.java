package com.buyorwait.finance;

import com.buyorwait.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.buyorwait.finance.FinanceTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ForecastEngineTest {
    private final LocalDate start = LocalDate.of(2026, 1, 1);

    @Test
    void debitOccursBeforeLaterSalary() {
        ForecastResult result = engine().simulate(profile(new BigDecimal("150"), new BigDecimal("100")), start,
                List.of(flow("rent", start.plusDays(1), EventDirection.DEBIT, "60"), flow("salary", start.plusDays(2), EventDirection.CREDIT, "80")), List.of(), List.of());
        assertEquals(ForecastStatus.UNSAFE, result.status());
        assertEquals(start.plusDays(1), result.firstViolationDate().orElseThrow());
    }

    @Test
    void salaryOccursBeforeDebit() {
        ForecastResult result = engine().simulate(profile(new BigDecimal("150"), new BigDecimal("100")), start,
                List.of(flow("salary", start.plusDays(1), EventDirection.CREDIT, "80"), flow("rent", start.plusDays(2), EventDirection.DEBIT, "60")), List.of(), List.of());
        assertTrue(result.safe());
    }

    @Test
    void detectsTemporaryViolationDespiteRecoveredEndingBalance() {
        ForecastResult result = engine().simulate(profile(new BigDecimal("120"), new BigDecimal("100")), start,
                List.of(flow("bill", start, EventDirection.DEBIT, "30"), flow("salary", start.plusDays(1), EventDirection.CREDIT, "100")), List.of(), List.of());
        assertEquals(ForecastStatus.UNSAFE, result.status());
        assertEquals(new BigDecimal("190"), result.endingBalance());
    }

    @Test
    void pendingDebitIsReservedOnRequestDate() {
        ResolutionResult resolution = resolver().resolve(profile(new BigDecimal("150"), new BigDecimal("100")), start,
                List.of(event("pending", EventType.EXPENSE, "rent", EventDirection.DEBIT, "30", CurrencyCode.INR, start.plusDays(4), EventStatus.PENDING, null)));
        ForecastResult result = engine().simulate(profile(new BigDecimal("150"), new BigDecimal("100")), start,
                resolution.cashFlows(), List.of(new ProposedPayment("buy", start, new BigDecimal("30"))), List.of(), resolution.unresolvedEvidence());
        assertEquals(ForecastStatus.UNSAFE, result.status());
        assertEquals(start, result.firstViolationDate().orElseThrow());
    }

    @Test
    void pendingCreditIsIgnored() {
        ResolutionResult resolution = resolver().resolve(profile(new BigDecimal("110"), new BigDecimal("100")), start,
                List.of(event("pending-credit", EventType.INCOME, "salary", EventDirection.CREDIT, "100", CurrencyCode.INR, start.plusDays(1), EventStatus.PENDING, null)));
        ForecastResult result = engine().simulate(profile(new BigDecimal("110"), new BigDecimal("100")), start,
                resolution.cashFlows(), List.of(new ProposedPayment("buy", start, new BigDecimal("20"))), List.of());
        assertEquals(ForecastStatus.UNSAFE, result.status());
    }

    @Test
    void cancelledDebitIsIgnored() {
        ResolutionResult resolution = resolver().resolve(profile(new BigDecimal("110"), new BigDecimal("100")), start,
                List.of(event("cancelled", EventType.EXPENSE, "rent", EventDirection.DEBIT, "50", CurrencyCode.INR, start.plusDays(1), EventStatus.CANCELLED, null)));
        assertTrue(engine().simulate(profile(new BigDecimal("110"), new BigDecimal("100")), start, resolution.cashFlows(), List.of(), List.of()).safe());
    }

    @Test
    void failedDebitWithScheduledLinkedRetryCountsOnlyRetry() {
        ResolutionResult resolution = resolver().resolve(profile(new BigDecimal("120"), new BigDecimal("100")), start, List.of(
                event("failed", EventType.EXPENSE, "rent", EventDirection.DEBIT, "50", CurrencyCode.INR, start, EventStatus.FAILED, null),
                event("retry", EventType.EXPENSE, "rent", EventDirection.DEBIT, "30", CurrencyCode.INR, start.plusDays(1), EventStatus.SCHEDULED, "failed")));
        assertEquals(1, resolution.cashFlows().size());
        assertEquals("retry", resolution.cashFlows().getFirst().flowId());
    }

    @Test
    void confirmedScheduledSalaryIsCounted() {
        ResolutionResult resolution = resolver().resolve(profile(new BigDecimal("100"), new BigDecimal("100")), start,
                List.of(event("salary", EventType.INCOME, "salary", EventDirection.CREDIT, "20", CurrencyCode.INR, start, EventStatus.SCHEDULED, null)));
        assertTrue(engine().simulate(profile(new BigDecimal("100"), new BigDecimal("100")), start, resolution.cashFlows(), List.of(), List.of()).safe());
    }

    @Test
    void scheduledBonusAndRefundAreIgnored() {
        ResolutionResult resolution = resolver().resolve(profile(new BigDecimal("100"), new BigDecimal("100")), start, List.of(
                event("bonus", EventType.INCOME, "windfall", EventDirection.CREDIT, "200", CurrencyCode.INR, start, EventStatus.SCHEDULED, null),
                event("refund", EventType.REFUND, "shopping", EventDirection.CREDIT, "200", CurrencyCode.INR, start, EventStatus.PENDING, null)));
        assertTrue(resolution.cashFlows().isEmpty());
    }

    @Test
    void repeatsEstablishedMonthlyEssentialDebit() {
        RecurrenceAnalyzer analyzer = new RecurrenceAnalyzer(converter());
        List<FinancialEvent> history = List.of(
                event("a", EventType.EXPENSE, "rent", EventDirection.DEBIT, "20", CurrencyCode.INR, LocalDate.of(2025, 10, 1), EventStatus.SETTLED, null),
                event("b", EventType.EXPENSE, "rent", EventDirection.DEBIT, "20", CurrencyCode.INR, LocalDate.of(2025, 11, 1), EventStatus.SETTLED, null),
                event("c", EventType.EXPENSE, "rent", EventDirection.DEBIT, "20", CurrencyCode.INR, LocalDate.of(2025, 12, 1), EventStatus.SETTLED, null));
        RecurrenceAnalysisResult result = analyzer.analyze(profile(new BigDecimal("200"), new BigDecimal("0")), start, history, List.of());
        assertEquals(3, result.cashFlows().size());
        assertEquals(new BigDecimal("20"), result.cashFlows().getFirst().amount());
    }

    @Test
    void doesNotRepeatAnIsolatedPurchase() {
        RecurrenceAnalyzer analyzer = new RecurrenceAnalyzer(converter());
        RecurrenceAnalysisResult result = analyzer.analyze(profile(new BigDecimal("200"), new BigDecimal("0")), start,
                List.of(event("one", EventType.EXPENSE, "shopping", EventDirection.DEBIT, "20", CurrencyCode.INR, start.minusDays(2), EventStatus.SETTLED, null)), List.of());
        assertTrue(result.cashFlows().isEmpty());
    }

    @Test
    void convertsForeignDebitUsingSettlementRate() {
        CurrencyConverter converter = FinanceTestSupport.converter(new ExchangeRate(start, CurrencyCode.USD, CurrencyCode.INR, new BigDecimal("80")));
        EventResolver resolver = new EventResolver(converter);
        ResolutionResult resolution = resolver.resolve(profile(new BigDecimal("1000"), new BigDecimal("0")), start,
                List.of(event("debit", EventType.EXPENSE, "rent", EventDirection.DEBIT, "2", CurrencyCode.USD, start, EventStatus.SCHEDULED, null)));
        assertEquals(new BigDecimal("160"), resolution.cashFlows().stream().filter(flow -> flow.flowId().equals("debit")).findFirst().orElseThrow().amount());
    }

    @Test
    void convertsForeignCreditUsingSettlementRate() {
        CurrencyConverter converter = FinanceTestSupport.converter(new ExchangeRate(start, CurrencyCode.USD, CurrencyCode.INR, new BigDecimal("80")));
        EventResolver resolver = new EventResolver(converter);
        ResolutionResult resolution = resolver.resolve(profile(new BigDecimal("1000"), new BigDecimal("0")), start,
                List.of(event("credit", EventType.INCOME, "salary", EventDirection.CREDIT, "3", CurrencyCode.USD, start, EventStatus.SCHEDULED, null)));
        assertEquals(new BigDecimal("240"), resolution.cashFlows().stream().filter(flow -> flow.flowId().equals("credit")).findFirst().orElseThrow().amount());
    }

    @Test
    void linkedCancelledAuthorizationDoesNotDoubleCountSettledCharge() {
        ResolutionResult resolution = resolver().resolve(profile(new BigDecimal("200"), new BigDecimal("0")), start, List.of(
                event("authorization", EventType.EXPENSE, "shopping", EventDirection.DEBIT, "50", CurrencyCode.INR, start, EventStatus.CANCELLED, null),
                event("charge", EventType.EXPENSE, "shopping", EventDirection.DEBIT, "50", CurrencyCode.INR, start.plusDays(1), EventStatus.SETTLED, "authorization")));
        assertEquals(1, resolution.cashFlows().size());
        assertEquals("charge", resolution.cashFlows().getFirst().flowId());
    }

    @Test
    void unresolvedImageBackedAmountProducesExplicitResult() {
        ResolutionResult resolution = resolver().resolve(profile(new BigDecimal("200"), new BigDecimal("0")), start,
                List.of(event("missing", EventType.EXPENSE, "rent", EventDirection.DEBIT, null, CurrencyCode.INR, start.plusDays(1), EventStatus.SCHEDULED, null)));
        ForecastResult result = engine().simulate(profile(new BigDecimal("200"), new BigDecimal("0")), start,
                resolution.cashFlows(), List.of(), List.of(), resolution.unresolvedEvidence());
        assertEquals(ForecastStatus.UNRESOLVED, result.status());
    }

    @Test
    void hypotheticalPaymentCanMakeSafeForecastUnsafe() {
        ForecastResult result = engine().simulate(profile(new BigDecimal("120"), new BigDecimal("100")), start, List.of(),
                List.of(new ProposedPayment("purchase", start, new BigDecimal("30"))), List.of());
        assertEquals(ForecastStatus.UNSAFE, result.status());
    }

    @Test
    void explicitOverrideWinsDuringConflictResolution() {
        FinancialEvent event = event("debit", EventType.EXPENSE, "rent", EventDirection.DEBIT, "20", CurrencyCode.INR,
                start.plusDays(1), EventStatus.SCHEDULED, null);
        EventResolutionOverride cancellation = new EventResolutionOverride("debit", "bank", java.time.Instant.parse("2026-01-01T10:00:00Z"),
                true, java.util.Optional.of(EventStatus.CANCELLED), java.util.Optional.empty(), java.util.Optional.empty());
        ResolutionResult result = resolver().resolve(profile(new BigDecimal("200"), new BigDecimal("0")), start,
                List.of(event), List.of(cancellation));
        assertTrue(result.cashFlows().isEmpty());
    }

    private EventResolver resolver() { return new EventResolver(converter()); }
    private CurrencyConverter converter() { return FinanceTestSupport.converter(); }
    private ForecastEngine engine() { return new ForecastEngine(); }
    private ResolvedCashFlow flow(String id, LocalDate date, EventDirection direction, String amount) {
        return new ResolvedCashFlow(id, java.util.Optional.of(id), date, direction, new BigDecimal(amount), CashFlowKind.RESOLVED_EVENT, false, Flexibility.FIXED, "rent");
    }
}
