package com.buyorwait.evaluation;

import com.buyorwait.decision.*;
import com.buyorwait.model.*;
import com.buyorwait.input.Dataset;
import com.buyorwait.output.DecisionCsvWriter;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SampleEvaluatorTest {
    @TempDir Path temporary;

    @Test
    void comparesStructuredValuesWithoutRoundingOrTreatingExplanationsAsSemanticScores() {
        assertTrue(SampleEvaluator.matches("amount_safe_to_pay", "10.00", "10"));
        assertFalse(SampleEvaluator.matches("amount_safe_to_pay", "10.00", "10.001"));
        assertTrue(SampleEvaluator.matches("payment_plan", "2026-01-01:10.00|2026-02-01:20", "2026-01-01:10|2026-02-01:20.0"));
        assertFalse(SampleEvaluator.matches("payment_plan", "2026-01-01:10", "2026-01-02:10"));
        assertTrue(SampleEvaluator.matches("spending_changes_needed", "stop:a|reduce_to:b:10.0", "reduce_to:b:10|stop:a"));
        assertFalse(SampleEvaluator.matches("decision_explanation", "One", "Two"));
    }

    @Test
    void writesExactOutputSchemaAndAllExpectedActualFieldComparisons() throws Exception {
        var decision = new AffordabilityDecision("sample", BigDecimal.ZERO, AffordabilityStatus.NOT_AFFORDABLE,
                PaymentMethod.NOT_RECOMMENDED, List.of(), Optional.empty(), List.of(), "Unresolved, requires \"evidence\".", List.of());
        var request = new FinanceRequest("sample", "user", LocalDate.parse("2026-01-01"), RequestType.PURCHASE,
                BigDecimal.TEN, LocalDate.parse("2026-02-01"), false, "Purchase");
        var profile = new FinancialProfile("user", CurrencyCode.INR, BigDecimal.ZERO, BigDecimal.ZERO, Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Optional.empty());
        var sample = new SampleRequest(request, new SampleOutput("2", "affordable_later", "wait", "2026-01-05:10", "2026-01-05", "none", "Expected"));
        var dataset = new Dataset(List.of(profile), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(sample));
        new SampleEvaluator().write(temporary, dataset, List.of(decision));
        try (var reader = Files.newBufferedReader(temporary.resolve("sample_predictions.csv"));
                var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            assertEquals(DecisionCsvWriter.COLUMNS, parser.getHeaderNames());
            assertEquals(decision.explanation(), parser.getRecords().getFirst().get("decision_explanation"));
        }
        try (var reader = Files.newBufferedReader(temporary.resolve("sample_evaluation.csv"));
                var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            var rows = parser.getRecords();
            assertEquals(8, rows.size());
            assertEquals("-2", rows.get(1).get("numeric_error_actual_minus_expected"));
        }
        assertTrue(Files.readString(temporary.resolve("sample_evaluation.md")).contains("literal text equality"));
        assertThrows(IllegalArgumentException.class, () -> new DecisionCsvWriter().write(temporary.resolve("bad.csv"), List.of(decision, decision)));
    }
}
