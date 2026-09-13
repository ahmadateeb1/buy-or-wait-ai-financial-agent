package com.buyorwait.diagnostic;

import com.buyorwait.finance.*;
import com.buyorwait.input.Dataset;
import com.buyorwait.input.DatasetIndex;
import com.buyorwait.model.EventDirection;
import com.buyorwait.model.FinancialProfile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Runs experimental amounts on copies of the production baseline's projected flows. */
public final class RecurrenceAmountComparison {
    private final DatasetIndex index;

    public RecurrenceAmountComparison(DatasetIndex index) { this.index = index; }

    public record Comparison(ForecastResult production, Map<RecurrenceAmountEstimator, ForecastResult> estimates,
            int variableDebitSeries) {
        public Comparison { estimates = Map.copyOf(estimates); }
    }

    public Comparison compare(FinancialProfile profile, LocalDate requestDate) {
        ForecastDiagnostic baseline = new CashFlowForecastService(index)
                .diagnose(profile, requestDate, List.of(), List.of());
        return compare(profile, requestDate, baseline);
    }

    private Comparison compare(FinancialProfile profile, LocalDate requestDate, ForecastDiagnostic baseline) {
        List<ResolvedCashFlow> explicit = baseline.cashFlows().stream()
                .filter(flow -> flow.kind() != CashFlowKind.RECURRENCE).toList();
        RecurrenceAnalysisResult recurrence = new RecurrenceAnalyzer(new CurrencyConverter(index))
                .analyze(profile, requestDate, index.eventsByUserId(profile.userId()), explicit);
        Map<String, List<BigDecimal>> variableHistories = new HashMap<>();
        for (ResolvedCashFlow flow : recurrence.cashFlows()) {
            if (flow.direction() != EventDirection.DEBIT) continue;
            String origin = flow.originEventId().orElseThrow();
            List<BigDecimal> amounts = recurrence.historicalAmountsByOriginEventId().get(origin);
            if (amounts.stream().anyMatch(amount -> amount.compareTo(amounts.getFirst()) != 0)) {
                variableHistories.put(origin, amounts);
            }
        }
        Map<RecurrenceAmountEstimator, ForecastResult> results = new EnumMap<>(RecurrenceAmountEstimator.class);
        for (RecurrenceAmountEstimator estimator : RecurrenceAmountEstimator.values()) {
            Map<String, BigDecimal> replacements = new HashMap<>();
            variableHistories.forEach((origin, amounts) -> replacements.put(origin, estimator.estimate(amounts)));
            List<ResolvedCashFlow> adjusted = baseline.cashFlows().stream()
                    .map(flow -> replaceProjectedDebit(flow, replacements)).toList();
            results.put(estimator, new ForecastEngine().simulate(profile, requestDate, adjusted, List.of(), List.of(),
                    baseline.result().unresolvedEvidence()));
        }
        return new Comparison(baseline.result(), results, variableHistories.size());
    }

    private static ResolvedCashFlow replaceProjectedDebit(ResolvedCashFlow flow, Map<String, BigDecimal> amounts) {
        if (flow.kind() != CashFlowKind.RECURRENCE || flow.direction() != EventDirection.DEBIT) return flow;
        BigDecimal amount = flow.originEventId().map(amounts::get).orElse(null);
        if (amount == null) return flow;
        return new ResolvedCashFlow(flow.flowId(), flow.originEventId(), flow.date(), flow.direction(), amount,
                flow.kind(), flow.recurring(), flow.flexibility(), flow.category());
    }

    public void printSamples(Dataset dataset, Appendable output) {
        List<String> header = new ArrayList<>(List.of("request_id", "home_currency", "production_minimum_balance"));
        for (RecurrenceAmountEstimator estimator : RecurrenceAmountEstimator.values()) {
            header.add(estimator.label() + "_minimum_balance");
        }
        header.addAll(List.of("expected_amount_safe_to_pay", "expected_affordability_status",
                "expected_earliest_date_for_full_payment", "variable_debit_series", "production_forecast_status",
                "unresolved_evidence"));
        try {
            CSVPrinter printer = new CSVPrinter(output, CSVFormat.DEFAULT.builder().setHeader(header.toArray(String[]::new)).build());
            for (var sample : dataset.sampleRequests()) {
                var request = sample.request();
                FinancialProfile profile = index.profileByUserId(request.userId())
                        .orElseThrow(() -> new IllegalArgumentException("Missing profile for sample " + request.requestId()));
                Comparison comparison = compare(profile, request.requestDate(),
                        new CashFlowForecastService(index).diagnose(request, List.of(), List.of()));
                List<Object> row = new ArrayList<>(List.of(request.requestId(), profile.homeCurrency(),
                        comparison.production().minimumProjectedBalance().toPlainString()));
                for (RecurrenceAmountEstimator estimator : RecurrenceAmountEstimator.values()) {
                    row.add(comparison.estimates().get(estimator).minimumProjectedBalance().toPlainString());
                }
                // Sample answers are displayed only; they never enter the simulations.
                var expected = sample.expectedOutput();
                row.addAll(List.of(expected.amountSafeToPay(), expected.affordabilityStatus(), expected.earliestDateForFullPayment(),
                        comparison.variableDebitSeries(), comparison.production().status(),
                        comparison.production().unresolvedEvidence().stream()
                                .map(issue -> issue.code() + ":" + issue.eventId().orElse("none"))
                                .collect(Collectors.joining("|"))));
                printer.printRecord(row);
            }
            printer.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot print recurrence amount comparison", exception);
        }
    }
}
