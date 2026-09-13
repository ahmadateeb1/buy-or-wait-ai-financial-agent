package com.buyorwait.finance;

import com.buyorwait.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.buyorwait.finance.FinanceTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class RecurrenceAnalyzerTest {
    @Test
    void payrollSequenceSurvivesLaterPromotionArrears() {
        List<FinancialEvent> history = List.of(
                credit("april-payroll", "2019-04-15", "4365000"),
                credit("may-payroll", "2019-05-15", "4365000"),
                credit("june-payroll", "2019-06-15", "4365000"),
                credit("july-payroll", "2019-07-15", "4365000"),
                credit("august-payroll", "2019-08-15", "4365000"),
                credit("promotion-arrears", "2019-08-20", "1964250"));

        RecurrenceAnalysisResult result = analyze("2019-09-03", history);

        assertTrue(result.unresolvedEvidence().isEmpty());
        assertEquals(List.of(LocalDate.parse("2019-09-15"), LocalDate.parse("2019-10-15"),
                LocalDate.parse("2019-11-15")), result.cashFlows().stream().map(ResolvedCashFlow::date).toList());
        for (ResolvedCashFlow flow : result.cashFlows()) {
            assertEquals(EventDirection.CREDIT, flow.direction());
            assertEquals(new BigDecimal("4365000"), flow.amount());
            assertEquals("august-payroll", flow.originEventId().orElseThrow());
            assertEquals(CashFlowKind.RECURRENCE, flow.kind());
            assertNotEquals(20, flow.date().getDayOfMonth());
        }
        assertEquals(result, analyze("2019-09-03", history.reversed()));
    }

    @Test
    void interleavedOneOffDebitsDoNotChangeWeeklyDatesOrMaximumSeriesAmount() {
        // Descriptions differ for every event, including the regular groceries.
        RecurrenceAnalysisResult result = analyze("2026-01-01", List.of(
                debit("weekly-shop-a", "2025-12-03", "20"),
                debit("unusual-shop", "2025-12-06", "900"),
                debit("weekly-shop-b", "2025-12-10", "35"),
                debit("weekly-shop-c", "2025-12-17", "25"),
                debit("weekly-shop-d", "2025-12-24", "30"),
                debit("another-one-off", "2025-12-29", "800")));

        List<LocalDate> expectedDates = LocalDate.parse("2026-01-07")
                .datesUntil(LocalDate.parse("2026-04-01"), java.time.Period.ofDays(7)).toList();
        assertEquals(expectedDates, result.cashFlows().stream().map(ResolvedCashFlow::date).toList());
        assertTrue(result.cashFlows().stream().allMatch(flow -> flow.amount().equals(new BigDecimal("35"))));
    }

    @Test
    void variableCreditUsesMinimumOfOnlyTheMonthlySequence() {
        RecurrenceAnalysisResult result = analyze("2019-09-03", List.of(
                credit("regular-a", "2019-04-15", "120"),
                credit("regular-b", "2019-05-15", "100"),
                credit("extra-credit", "2019-05-20", "1"),
                credit("regular-c", "2019-06-15", "130"),
                credit("regular-d", "2019-07-15", "110"),
                credit("regular-e", "2019-08-15", "115")));

        assertEquals(3, result.cashFlows().size());
        assertTrue(result.cashFlows().stream().allMatch(flow -> flow.amount().equals(new BigDecimal("100"))));
        assertTrue(result.cashFlows().stream().allMatch(flow -> flow.date().getDayOfMonth() == 15));
    }

    @Test
    void longestSequenceWinsOverShorterRecentSequence() {
        RecurrenceAnalysisResult result = analyze("2019-09-03", List.of(
                credit("regular-a", "2019-04-15", "100"),
                credit("regular-b", "2019-05-15", "100"),
                credit("regular-c", "2019-06-15", "100"),
                credit("regular-d", "2019-07-15", "100"),
                credit("regular-e", "2019-08-15", "100"),
                credit("short-series-a", "2019-08-20", "5"),
                credit("short-series-b", "2019-08-23", "5"),
                credit("short-series-c", "2019-08-26", "5")));

        assertEquals(3, result.cashFlows().size());
        assertTrue(result.cashFlows().stream().allMatch(flow -> flow.date().getDayOfMonth() == 15));
        assertTrue(result.cashFlows().stream().allMatch(flow -> flow.amount().equals(new BigDecimal("100"))));
    }

    private RecurrenceAnalysisResult analyze(String requestDate, List<FinancialEvent> history) {
        return new RecurrenceAnalyzer(converter()).analyze(profile(new BigDecimal("10000"), BigDecimal.ZERO),
                LocalDate.parse(requestDate), history, List.of());
    }

    private FinancialEvent credit(String id, String date, String amount) {
        return event(id, EventType.INCOME, "salary", EventDirection.CREDIT, amount, CurrencyCode.INR,
                LocalDate.parse(date), EventStatus.SETTLED, null);
    }

    private FinancialEvent debit(String id, String date, String amount) {
        return event(id, EventType.EXPENSE, "groceries", EventDirection.DEBIT, amount, CurrencyCode.INR,
                LocalDate.parse(date), EventStatus.SETTLED, null);
    }
}
