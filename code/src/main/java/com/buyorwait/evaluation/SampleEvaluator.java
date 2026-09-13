package com.buyorwait.evaluation;

import com.buyorwait.decision.*;
import com.buyorwait.input.Dataset;
import com.buyorwait.output.DecisionCsvWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Reads expected values only after predictions have been generated. Never feeds labels into decisions. */
public final class SampleEvaluator {
    public void write(Path directory, Dataset dataset, List<AffordabilityDecision> predictions) throws IOException {
        Files.createDirectories(directory);
        new DecisionCsvWriter().write(directory.resolve("sample_predictions.csv"), predictions);
        Map<String, AffordabilityDecision> byId = new HashMap<>();
        predictions.forEach(decision -> byId.put(decision.requestId(), decision));
        int[] matches = new int[DecisionCsvWriter.COLUMNS.size()];
        int allDecisionFields = 0;
        Map<String, BigDecimal> absoluteErrors = new TreeMap<>();
        Map<String, Integer> currencyCounts = new TreeMap<>();
        List<String> unresolved = new ArrayList<>();
        try (var writer = Files.newBufferedWriter(directory.resolve("sample_evaluation.csv"), StandardCharsets.UTF_8);
                var printer = new CSVPrinter(writer, CSVFormat.RFC4180.builder().setRecordSeparator("\n")
                        .setHeader("request_id", "field", "expected", "actual", "match", "numeric_error_actual_minus_expected").build())) {
            for (var sample : dataset.sampleRequests()) {
                var expected = sample.expectedOutput();
                List<String> fields = List.of(sample.request().requestId(), expected.amountSafeToPay(), expected.affordabilityStatus(),
                        expected.recommendedPaymentMethod(), expected.paymentPlan(), expected.earliestDateForFullPayment(),
                        expected.spendingChangesNeeded(), expected.decisionExplanation());
                AffordabilityDecision decision = Objects.requireNonNull(byId.get(sample.request().requestId()), "Missing sample prediction");
                boolean all = true;
                for (int i = 0; i < fields.size(); i++) {
                    boolean match = matches(DecisionCsvWriter.COLUMNS.get(i), fields.get(i), decision.columns().get(i));
                    if (match) matches[i]++;
                    else if (i > 0 && i < 7) all = false;
                    String error = i == 1 ? AffordabilityDecision.decimal(decision.amountSafeToPay().subtract(new BigDecimal(fields.get(i)))) : "";
                    printer.printRecord(decision.requestId(), DecisionCsvWriter.COLUMNS.get(i), fields.get(i), decision.columns().get(i), match, error);
                }
                if (all) allDecisionFields++;
                String currency = dataset.profiles().stream().filter(profile -> profile.userId().equals(sample.request().userId()))
                        .findFirst().orElseThrow().homeCurrency().csvValue();
                absoluteErrors.merge(currency, decision.amountSafeToPay().subtract(new BigDecimal(expected.amountSafeToPay())).abs(), BigDecimal::add);
                currencyCounts.merge(currency, 1, Integer::sum);
                if (!decision.unresolvedEvidence().isEmpty()) unresolved.add(decision.requestId() + ": " + decision.unresolvedEvidence().stream()
                        .map(issue -> issue.code() + issue.eventId().map(id -> ":" + id).orElse(""))
                        .distinct().collect(java.util.stream.Collectors.joining(", ")));
            }
        }
        StringBuilder report = new StringBuilder("# Sample evaluation\n\nEvaluated " + predictions.size()
                + " solved samples with the production pipeline. Expected values were read only for this report.\n\n"
                + "| Field | Matches | Total |\n| --- | ---: | ---: |\n");
        for (int i = 0; i < matches.length; i++) report.append("| ").append(DecisionCsvWriter.COLUMNS.get(i)).append(" | ")
                .append(matches[i]).append(" | ").append(predictions.size()).append(" |\n");
        report.append("\nAll six structured decision fields matched for ").append(allDecisionFields).append("/")
                .append(predictions.size()).append(" samples (excluding ID and explanation).\n\n")
                .append("Amounts are compared as exact BigDecimal values, ignoring trailing zeros. Payment plans compare dates and exact amounts in order. ")
                .append("Spending changes compare actions independent of order. Explanation matching is literal text equality, not a quality or groundedness score.\n\n")
                .append("Full expected/actual comparisons for every field and request: [sample_evaluation.csv](sample_evaluation.csv). ")
                .append("Predictions: [sample_predictions.csv](sample_predictions.csv).\n\n")
                .append("## Amount error by currency\n\n| Currency | Samples | Mean absolute error |\n| --- | ---: | ---: |\n");
        absoluteErrors.forEach((currency, sum) -> report.append("| ").append(currency).append(" | ").append(currencyCounts.get(currency))
                .append(" | ").append(sum.divide(BigDecimal.valueOf(currencyCounts.get(currency)), 2, RoundingMode.HALF_UP).toPlainString()).append(" |\n"));
        report.append("\n## Evidence limitations\n\n").append(unresolved.isEmpty() ? "No unresolved sample forecasts.\n" : String.join("\n", unresolved.stream().map(s -> "- " + s).toList()) + "\n")
                .append("\nUnresolved forecasts use 0 / not_affordable / not_recommended with an explicit evidence explanation; this is a conservative output fallback, not proof of inability to pay. ")
                .append("No image extraction or model calls were added. Unsupported message interpretations remain outside candidate validation.\n\n")
                .append("## Fixed implementation interpretations\n\n")
                .append("- The horizon is request_date through request_date + 89 days; it does not restart at a proposed payment date.\n")
                .append("- Existing same-day ordering is unchanged: ordinary debits, proposed payments, then credits.\n")
                .append("- Deadlines and the horizon are hard candidate limits. Earliest full-payment capacity is calculated independently of preferences and the deadline.\n")
                .append("- Installment duration is checked by final due date against first payment date plus max_installment_months calendar months; the specification does not provide a more precise tenure formula.\n")
                .append("- Spending-change search enumerates up to three distinct eligible events, using stop or the supplied minimum_allowed_amount for reductions. It does not optimize how small a reduction can be.\n")
                .append("- Ranking uses the specification's order. All candidates already satisfy the deadline; ties after the supplied option ID retain deterministic generation order.\n")
                .append("- Recurrence detection and its conservative variable-debit maximum were not tuned to the sample labels. Disagreements are retained for review.\n");
        Files.writeString(directory.resolve("sample_evaluation.md"), report, StandardCharsets.UTF_8);
    }

    static boolean matches(String field, String expected, String actual) {
        if (field.equals("amount_safe_to_pay")) return new BigDecimal(expected).compareTo(new BigDecimal(actual)) == 0;
        if (field.equals("payment_plan")) return normalizedPlan(expected).equals(normalizedPlan(actual));
        if (field.equals("spending_changes_needed")) return normalizedActions(expected).equals(normalizedActions(actual));
        return expected.equals(actual);
    }

    private static List<String> normalizedPlan(String value) {
        if (value.equals("none")) return List.of();
        return Arrays.stream(value.split("\\|")).map(part -> {
            String[] pieces = part.split(":", 2);
            return pieces[0] + ":" + AffordabilityDecision.decimal(new BigDecimal(pieces[1]));
        }).toList();
    }

    private static List<String> normalizedActions(String value) {
        if (value.equals("none")) return List.of();
        return Arrays.stream(value.split("\\|")).map(part -> {
            String[] pieces = part.split(":");
            return pieces.length == 3 ? pieces[0] + ":" + pieces[1] + ":" + AffordabilityDecision.decimal(new BigDecimal(pieces[2])) : part;
        }).sorted().toList();
    }
}
