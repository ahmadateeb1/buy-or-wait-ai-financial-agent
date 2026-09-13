package com.buyorwait.finance;

import com.buyorwait.diagnostic.RecurrenceAmountComparison;
import com.buyorwait.diagnostic.RecurrenceAmountEstimator;
import com.buyorwait.input.Dataset;
import com.buyorwait.input.DatasetIndex;
import com.buyorwait.model.*;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.buyorwait.finance.FinanceTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class RecurrenceAmountComparisonTest {
    @Test
    void calculatesAllSixEstimatorsWithoutFloatingPoint() {
        List<BigDecimal> amounts = List.of("90", "10", "30", "20", "40", "50").stream().map(BigDecimal::new).toList();
        List<BigDecimal> expected = List.of(new BigDecimal("90"), new BigDecimal("40"), new BigDecimal("35"),
                new BigDecimal("110").divide(new BigDecimal("3"), MathContext.DECIMAL128),
                new BigDecimal("30"), new BigDecimal("50"));
        for (int i = 0; i < RecurrenceAmountEstimator.values().length; i++) {
            assertEquals(0, expected.get(i).compareTo(RecurrenceAmountEstimator.values()[i].estimate(amounts)));
        }
        assertEquals(new BigDecimal("30"), RecurrenceAmountEstimator.HISTORICAL_MEDIAN.estimate(amounts.subList(0, 5)));
    }

    @Test
    void shortWindowsUseAvailableHistoryAndEmptyHistoryIsRejected() {
        List<BigDecimal> amounts = List.of(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"));
        assertEquals(0, new BigDecimal("20").compareTo(RecurrenceAmountEstimator.RECENT_5_MEAN.estimate(amounts)));
        for (RecurrenceAmountEstimator estimator : RecurrenceAmountEstimator.values()) {
            assertThrows(IllegalArgumentException.class, () -> estimator.estimate(List.of()));
        }
    }

    @Test
    void changesOnlyVariableProjectedDebitsAndPreservesProductionBaseline() {
        Dataset dataset = dataset(false);
        DatasetIndex index = new DatasetIndex(dataset);
        var profile = dataset.profiles().getFirst();
        LocalDate start = LocalDate.parse("2026-01-01");
        ForecastResult before = new CashFlowForecastService(index).simulate(profile, start, List.of(), List.of());
        var comparison = new RecurrenceAmountComparison(index).compare(profile, start);
        assertEquals(before, comparison.production());
        assertEquals(before, comparison.estimates().get(RecurrenceAmountEstimator.CURRENT_MAX));
        assertEquals(1, comparison.variableDebitSeries());
        for (var estimator : RecurrenceAmountEstimator.values()) {
            ForecastResult result = comparison.estimates().get(estimator);
            assertEquals(before.timeline().size(), result.timeline().size());
            for (int i = 0; i < result.timeline().size(); i++) {
                var original = before.timeline().get(i);
                var changed = result.timeline().get(i);
                assertEquals(original.entryId(), changed.entryId());
                assertEquals(original.date(), changed.date());
                BigDecimal expected = original.entryId().startsWith("recurrence:variable-12:")
                        ? estimator.estimate(List.of(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30")))
                        : original.amount();
                assertEquals(0, expected.compareTo(changed.amount()));
            }
        }
        assertEquals(before, new CashFlowForecastService(index).simulate(profile, start, List.of(), List.of()));
    }

    @Test
    void printsSampleExpectationsAndPropagatesUnresolvedEvidence() throws Exception {
        Dataset base = dataset(true);
        var request = new FinanceRequest("sample-only", "user", LocalDate.parse("2026-01-01"), RequestType.PURCHASE,
                new BigDecimal("123"), LocalDate.parse("2026-02-01"), false, "Sample");
        var sample = new SampleRequest(request, new SampleOutput("17.50", "affordable_later", "wait", "none", "",
                "none", "Example"));
        Dataset dataset = new Dataset(base.profiles(), base.events(), base.exchangeRates(), List.of(), List.of(),
                List.of(), List.of(), List.of(sample));
        var diagnostic = new RecurrenceAmountComparison(new DatasetIndex(dataset));
        var comparison = diagnostic.compare(dataset.profiles().getFirst(), request.requestDate());
        assertEquals(ForecastStatus.UNRESOLVED, comparison.production().status());
        comparison.estimates().values().forEach(result -> {
            assertEquals(ForecastStatus.UNRESOLVED, result.status());
            assertEquals(comparison.production().unresolvedEvidence(), result.unresolvedEvidence());
        });
        StringBuilder output = new StringBuilder();
        diagnostic.printSamples(dataset, output);
        try (var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(new java.io.StringReader(output.toString()))) {
            var rows = parser.getRecords();
            assertEquals(1, rows.size());
            assertEquals("sample-only", rows.getFirst().get("request_id"));
            assertEquals("17.50", rows.getFirst().get("expected_amount_safe_to_pay"));
            assertEquals("affordable_later", rows.getFirst().get("expected_affordability_status"));
            assertEquals("", rows.getFirst().get("expected_earliest_date_for_full_payment"));
            assertEquals("UNRESOLVED", rows.getFirst().get("production_forecast_status"));
            assertTrue(rows.getFirst().get("unresolved_evidence").contains("unresolved_amount:missing"));
        }
    }

    private Dataset dataset(boolean missingAmount) {
        List<FinancialEvent> events = new ArrayList<>();
        for (int month = 10; month <= 12; month++) {
            LocalDate date = LocalDate.of(2025, month, 1);
            events.add(event("variable-" + month, EventType.EXPENSE, "groceries", EventDirection.DEBIT,
                    Integer.toString((month - 9) * 10), CurrencyCode.INR, date, EventStatus.SETTLED, null));
            events.add(event("fixed-" + month, EventType.EXPENSE, "rent", EventDirection.DEBIT,
                    "15", CurrencyCode.INR, date, EventStatus.SETTLED, null));
            events.add(event("credit-" + month, EventType.INCOME, "salary", EventDirection.CREDIT,
                    Integer.toString(month * 10), CurrencyCode.INR, date, EventStatus.SETTLED, null));
        }
        events.add(event("one-off", EventType.EXPENSE, "groceries", EventDirection.DEBIT, "999", CurrencyCode.INR,
                LocalDate.parse("2025-12-20"), EventStatus.SETTLED, null));
        events.add(event("pending", EventType.EXPENSE, "groceries", EventDirection.DEBIT, "7", CurrencyCode.INR,
                LocalDate.parse("2026-01-02"), EventStatus.PENDING, null));
        events.add(event("scheduled", EventType.EXPENSE, "groceries", EventDirection.DEBIT, "9", CurrencyCode.INR,
                LocalDate.parse("2026-01-03"), EventStatus.SCHEDULED, null));
        if (missingAmount) events.add(event("missing", EventType.EXPENSE, "health", EventDirection.DEBIT, null, CurrencyCode.INR,
                LocalDate.parse("2026-01-04"), EventStatus.SCHEDULED, null));
        return new Dataset(List.of(profile(new BigDecimal("1000"), BigDecimal.ZERO)), events, List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
