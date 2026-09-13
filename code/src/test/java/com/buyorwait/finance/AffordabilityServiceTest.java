package com.buyorwait.finance;

import com.buyorwait.decision.*;
import com.buyorwait.input.*;
import com.buyorwait.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AffordabilityServiceTest {
    private final LocalDate start = LocalDate.parse("2026-01-01");

    @Test
    void fullPaymentTodayCapsCapacityAtRequestAndUsesExactDecimalArithmetic() {
        var decision = decide(profile("200.15", Set.of(PaymentMethod.FULL_PAYMENT)), request("70.12", false, 89), List.of(), List.of(),
                List.of(option("full", PaymentMethod.FULL_PAYMENT, "70.12", 1, 0, 0, "0")));
        assertEquals(new BigDecimal("70.12"), decision.amountSafeToPay());
        assertEquals(AffordabilityStatus.AFFORDABLE_NOW, decision.status());
        assertEquals(Optional.of(start), decision.earliestFullPayment());
        assertEquals("2026-01-01:70.12", decision.paymentPlan());
    }

    @Test
    void partialPaymentUsesTemporaryMinimumAndExactRemainderAtIndependentEarliestDate() {
        var profile = profile("200", Set.of(PaymentMethod.FULL_PAYMENT, PaymentMethod.PARTIAL_PAYMENT));
        var request = request("100", true, 89);
        var flows = List.of(flow("bill", 1, "30", EventDirection.DEBIT, false, "rent", Flexibility.FIXED),
                flow("salary", 2, "100", EventDirection.CREDIT, false, "salary", Flexibility.FIXED));
        var decision = decide(profile, request, flows, List.of(), List.of(option("full", PaymentMethod.FULL_PAYMENT, "100", 1, 0, 0, "0")));
        assertEquals(new BigDecimal("70"), decision.amountSafeToPay());
        assertEquals(PaymentMethod.PARTIAL_PAYMENT, decision.method());
        assertEquals(Optional.of(start.plusDays(3)), decision.earliestFullPayment());
        assertEquals("2026-01-01:70|2026-01-04:30", decision.paymentPlan());
        assertTrue(new ForecastEngine().simulate(profile, start, flows, decision.payments(), decision.changes()).safe());
    }

    @Test
    void disablingPartialOrMissingUserConsentFallsBackToWait() {
        var flows = List.of(flow("salary", 2, "100", EventDirection.CREDIT, false, "salary", Flexibility.FIXED));
        var option = option("full", PaymentMethod.FULL_PAYMENT, "100", 1, 0, 0, "0");
        assertEquals(PaymentMethod.WAIT, decide(profile("150", Set.of(PaymentMethod.FULL_PAYMENT, PaymentMethod.PARTIAL_PAYMENT)),
                request("100", false, 89), flows, List.of(), List.of(option)).method());
        assertEquals(PaymentMethod.WAIT, decide(profile("150", Set.of(PaymentMethod.FULL_PAYMENT)),
                request("100", true, 89), flows, List.of(), List.of(option)).method());
    }

    @Test
    void baselineRecoveryDoesNotHideAnEarlierViolation() {
        var flows = List.of(flow("bill", 1, "60", EventDirection.DEBIT, false, "rent", Flexibility.FIXED),
                flow("salary", 2, "100", EventDirection.CREDIT, false, "salary", Flexibility.FIXED));
        var decision = decide(profile("150", Set.of(PaymentMethod.FULL_PAYMENT)), request("20", false, 89), flows, List.of(),
                List.of(option("full", PaymentMethod.FULL_PAYMENT, "20", 1, 0, 0, "0")));
        assertEquals(BigDecimal.ZERO, decision.amountSafeToPay());
        assertTrue(decision.earliestFullPayment().isEmpty());
        assertEquals(PaymentMethod.NOT_RECOMMENDED, decision.method());
    }

    @Test
    void suppliedInstallmentsRespectScheduleAndCapacityIgnoresMethodPreferences() {
        var decision = decide(profile("300", Set.of(PaymentMethod.INSTALLMENTS)), request("150", false, 89), List.of(), List.of(),
                List.of(option("installment", PaymentMethod.INSTALLMENTS, "50", 3, 0, 30, "0")));
        assertEquals(PaymentMethod.INSTALLMENTS, decision.method());
        assertEquals(AffordabilityStatus.AFFORDABLE_WITH_PLAN, decision.status());
        assertEquals(Optional.of(start), decision.earliestFullPayment());
        assertEquals("2026-01-01:50|2026-01-31:50|2026-03-02:50", decision.paymentPlan());
    }

    @Test
    void installmentsOutsideDeadlineHorizonOrUserTenureAreRejected() {
        var profile = profile("1000", Set.of(PaymentMethod.INSTALLMENTS));
        assertEquals(PaymentMethod.NOT_RECOMMENDED, decide(profile, request("100", false, 89), List.of(), List.of(),
                List.of(option("long", PaymentMethod.INSTALLMENTS, "25", 4, 0, 30, "0"))).method());
        assertEquals(PaymentMethod.NOT_RECOMMENDED, decide(profile, request("100", false, 10), List.of(), List.of(),
                List.of(option("late", PaymentMethod.INSTALLMENTS, "50", 2, 0, 30, "0"))).method());
        var shortTenure = new FinancialProfile(profile.userId(), profile.homeCurrency(), profile.currentAvailableBalance(), profile.minimumBalanceToKeep(),
                Set.of(), Set.of(), Set.of(), Set.of(), profile.paymentMethodsUserWillConsider(), Optional.of(1));
        assertEquals(PaymentMethod.NOT_RECOMMENDED, decide(shortTenure, request("100", false, 89), List.of(), List.of(),
                List.of(option("tenure", PaymentMethod.INSTALLMENTS, "50", 2, 0, 40, "0"))).method());
    }

    @Test
    void cheapestSafePlanRanksBeforeEarlierFinancedPlan() {
        var flows = List.of(flow("salary", 10, "100", EventDirection.CREDIT, false, "salary", Flexibility.FIXED));
        var decision = decide(profile("150", Set.of(PaymentMethod.FULL_PAYMENT, PaymentMethod.INSTALLMENTS)), request("100", false, 89), flows, List.of(),
                List.of(option("full", PaymentMethod.FULL_PAYMENT, "100", 1, 0, 0, "0"),
                        option("financed", PaymentMethod.INSTALLMENTS, "35", 3, 0, 30, "5")));
        assertEquals(PaymentMethod.WAIT, decision.method());
        assertEquals(Optional.of(start.plusDays(11)), decision.earliestFullPayment());
    }

    @Test
    void optionIdBreaksOtherwiseEqualRankingAndMalformedTotalsAreRejected() {
        var profile = profile("300", Set.of(PaymentMethod.INSTALLMENTS));
        var decision = decide(profile, request("100", false, 89), List.of(), List.of(), List.of(
                option("offer_b", PaymentMethod.INSTALLMENTS, "50", 2, 0, 30, "0"),
                option("offer_a", PaymentMethod.INSTALLMENTS, "50", 2, 0, 30, "0")));
        assertEquals("offer_a:0", decision.payments().getFirst().paymentId());
        var malformed = new PaymentOption("bad", "request", PaymentMethod.INSTALLMENTS, new BigDecimal("50"), 2, start,
                Optional.of(30), BigDecimal.ZERO, new BigDecimal("99"));
        assertEquals(PaymentMethod.NOT_RECOMMENDED, decide(profile, request("100", false, 89), List.of(), List.of(), List.of(malformed)).method());
    }

    @Test
    void twoPermittedReductionsCanMakeRequestSafeWithoutChangingBaselineCapacity() {
        var profile = flexibleProfile(Set.of());
        var events = List.of(reducible("dining", "dining", "70", "20"), reducible("shopping", "shopping", "50", "10"));
        var flows = List.of(flow("dining", 1, "70", EventDirection.DEBIT, true, "dining", Flexibility.REDUCIBLE),
                flow("shopping", 2, "50", EventDirection.DEBIT, true, "shopping", Flexibility.REDUCIBLE));
        var decision = decide(profile, request("100", false, 89), flows, events,
                List.of(option("full", PaymentMethod.FULL_PAYMENT, "100", 1, 0, 0, "0")));
        assertEquals(new BigDecimal("30"), decision.amountSafeToPay());
        assertTrue(decision.earliestFullPayment().isEmpty());
        assertEquals(AffordabilityStatus.AFFORDABLE_WITH_PLAN, decision.status());
        assertEquals(2, decision.changes().size());
        assertTrue(new ForecastEngine().simulate(profile, start, flows, decision.payments(), decision.changes()).safe());
    }

    @Test
    void protectedCategoriesCannotBeReducedEvenIfListedAsAdjustable() {
        var profile = flexibleProfile(Set.of("dining"));
        var decision = decide(profile, request("100", false, 89),
                List.of(flow("dining", 1, "120", EventDirection.DEBIT, true, "dining", Flexibility.REDUCIBLE)),
                List.of(reducible("dining", "dining", "120", "20")),
                List.of(option("full", PaymentMethod.FULL_PAYMENT, "100", 1, 0, 0, "0")));
        assertEquals(PaymentMethod.NOT_RECOMMENDED, decision.method());
        assertTrue(decision.changes().isEmpty());
    }

    @Test
    void safeWaitWithoutChangesRanksBeforeImmediatePaymentRequiringChanges() {
        var profile = new FinancialProfile("user", CurrencyCode.INR, new BigDecimal("250"), new BigDecimal("100"), Set.of(), Set.of(),
                Set.of("dining"), Set.of(), Set.of(PaymentMethod.FULL_PAYMENT), Optional.empty());
        var flows = List.of(flow("dining", 1, "100", EventDirection.DEBIT, true, "dining", Flexibility.REDUCIBLE),
                flow("salary", 10, "200", EventDirection.CREDIT, false, "salary", Flexibility.FIXED));
        var decision = decide(profile, request("150", false, 89), flows, List.of(reducible("dining", "dining", "100", "0")),
                List.of(option("full", PaymentMethod.FULL_PAYMENT, "150", 1, 0, 0, "0")));
        assertEquals(PaymentMethod.WAIT, decision.method());
        assertTrue(decision.changes().isEmpty());
    }

    @Test
    void aPlanRequiringFourSpendingChangesIsRejected() {
        List<ResolvedCashFlow> flows = new ArrayList<>();
        List<FinancialEvent> events = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            String id = "flexible-" + i;
            flows.add(flow(id, i, "10", EventDirection.DEBIT, true, "dining", Flexibility.REDUCIBLE));
            events.add(reducible(id, "dining", "10", "0"));
        }
        var decision = decide(flexibleProfile(Set.of()), request("150", false, 89), flows, events,
                List.of(option("full", PaymentMethod.FULL_PAYMENT, "150", 1, 0, 0, "0")));
        assertEquals(PaymentMethod.NOT_RECOMMENDED, decision.method());
        assertTrue(decision.changes().isEmpty());
    }

    @Test
    void unresolvedEvidenceCannotProduceAPayment() {
        var profile = profile("1000", Set.of(PaymentMethod.FULL_PAYMENT));
        var issue = ForecastIssue.forEvent("unresolved_amount", "Image amount missing", "image-event");
        var result = new ForecastEngine().simulate(profile, start, List.of(), List.of(), List.of(), List.of(issue));
        var service = new AffordabilityService(new DatasetIndex(dataset(profile, List.of())));
        var decision = service.decide(request("20", false, 89), profile, new ForecastDiagnostic(result, List.of()),
                List.of(option("full", PaymentMethod.FULL_PAYMENT, "20", 1, 0, 0, "0")));
        assertEquals(BigDecimal.ZERO, decision.amountSafeToPay());
        assertEquals(PaymentMethod.NOT_RECOMMENDED, decision.method());
        assertTrue(decision.explanation().contains("unresolved_amount:image-event"));
    }

    private AffordabilityDecision decide(FinancialProfile profile, FinanceRequest request, List<ResolvedCashFlow> flows,
            List<FinancialEvent> events, List<PaymentOption> options) {
        var context = new ForecastDiagnostic(new ForecastEngine().simulate(profile, start, flows, List.of(), List.of()), flows);
        return new AffordabilityService(new DatasetIndex(dataset(profile, events))).decide(request, profile, context, options);
    }

    private Dataset dataset(FinancialProfile profile, List<FinancialEvent> events) {
        return new Dataset(List.of(profile), events, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private FinancialProfile profile(String balance, Set<PaymentMethod> methods) {
        return new FinancialProfile("user", CurrencyCode.INR, new BigDecimal(balance), new BigDecimal("100"), Set.of(), Set.of(),
                Set.of(), Set.of(), methods, Optional.of(3));
    }

    private FinancialProfile flexibleProfile(Set<String> protectedCategories) {
        return new FinancialProfile("user", CurrencyCode.INR, new BigDecimal("250"), new BigDecimal("100"), Set.of(), protectedCategories,
                Set.of("dining", "shopping"), Set.of(), Set.of(PaymentMethod.FULL_PAYMENT), Optional.empty());
    }

    private FinanceRequest request(String amount, boolean partial, int deadlineDays) {
        return new FinanceRequest("request", "user", start, RequestType.PURCHASE, new BigDecimal(amount), start.plusDays(deadlineDays), partial, "Purchase");
    }

    private PaymentOption option(String id, PaymentMethod method, String amount, int count, int firstDay, int frequency, String fee) {
        BigDecimal payment = new BigDecimal(amount);
        return new PaymentOption(id, "request", method, payment, count, start.plusDays(firstDay), frequency == 0 ? Optional.empty() : Optional.of(frequency),
                new BigDecimal(fee), payment.multiply(BigDecimal.valueOf(count)));
    }

    private ResolvedCashFlow flow(String id, int day, String amount, EventDirection direction, boolean recurring, String category, Flexibility flexibility) {
        return new ResolvedCashFlow(id, Optional.of(id), start.plusDays(day), direction, new BigDecimal(amount),
                recurring ? CashFlowKind.RECURRENCE : CashFlowKind.RESOLVED_EVENT, recurring, flexibility, category);
    }

    private FinancialEvent reducible(String id, String category, String amount, String minimum) {
        return new FinancialEvent(id, "user", EventType.EXPENSE, "Flexible spending", category, EventDirection.DEBIT,
                Optional.of(new BigDecimal(amount)), CurrencyCode.INR, start.minusMonths(1), Optional.of(start.minusMonths(1)),
                EventStatus.SETTLED, Optional.empty(), Flexibility.REDUCIBLE, Optional.of(new BigDecimal(minimum)));
    }
}
